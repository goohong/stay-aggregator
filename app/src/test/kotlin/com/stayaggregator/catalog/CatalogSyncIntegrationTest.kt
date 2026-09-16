package com.stayaggregator.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 목록 동기화가 실제 PostgreSQL 에 매핑을 남기는지 확인한다 (ADR-0035).
 *
 * 공급사는 가짜 어댑터로 대신한다. 공급사 HTTP 호출은 어댑터 테스트에서 따로 보고,
 * 여기서는 동기화 → 정규화 → 저장의 흐름과 트랜잭션 동작을 본다.
 * 테스트 사이에는 테이블을 비워 되돌린다 (ADR-0039).
 */
@SpringBootTest(properties = ["stay.catalog.sync.enabled=false"])
@Import(CatalogSyncIntegrationTest.Containers::class)
class CatalogSyncIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Containers {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
    }

    @Autowired
    private lateinit var normalizer: CatalogNormalizer

    @Autowired
    private lateinit var repository: MappingRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @BeforeEach
    fun clearTables() {
        jdbcClient.sql("truncate table room_type_mapping, hotel_mapping").update()
    }

    @Test
    fun `목록을 받으면 숙소와 객실 타입 매핑이 저장된다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))

        assertThat(hotelCodes()).containsExactly("A-1")
        assertThat(roomTypeCodes()).containsExactly("DLX")
    }

    @Test
    fun `다시 동기화해도 내부 식별자가 그대로다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val firstId = hotelId("A-1")

        sync(catalog(hotel("A-1", "이름이 바뀐 호텔", roomType("DLX", "디럭스", 2))))

        assertThat(hotelId("A-1")).isEqualTo(firstId)
        assertThat(hotelName("A-1")).isEqualTo("이름이 바뀐 호텔")
    }

    @Test
    fun `목록에서 빠지면 사라진 시각이 찍히고 다시 나타나면 풀린다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val firstId = hotelId("A-1")

        sync(catalog())
        assertThat(missingSince("A-1")).isNotNull()

        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        assertThat(missingSince("A-1")).isNull()
        assertThat(hotelId("A-1")).isEqualTo(firstId)
    }

    @Test
    fun `공급사 하나가 실패해도 다른 공급사는 반영된다`() {
        val failing = FakeCatalogAdapter("a", Mono.error(SupplierResponseException("읽을 수 없는 응답")))
        val working = FakeCatalogAdapter("b", Mono.just(FetchedCatalog("b", listOf(hotel("B-1", "한옥 스테이", roomType("ONDOL", "온돌", 2))))))

        CatalogSyncService(listOf(failing, working), normalizer, repository).syncAll()

        assertThat(hotelCodes()).containsExactly("B-1")
    }

    private fun sync(catalog: FetchedCatalog) {
        CatalogSyncService(listOf(FakeCatalogAdapter(catalog.supplierId, Mono.just(catalog))), normalizer, repository).syncAll()
    }

    private class FakeCatalogAdapter(
        override val supplierId: String,
        private val response: Mono<FetchedCatalog>,
    ) : CatalogAdapter {
        override fun fetchCatalog(): Mono<FetchedCatalog> = response
    }

    private fun catalog(vararg hotels: FetchedHotel) = FetchedCatalog("a", hotels.toList())

    private fun hotel(code: String?, name: String?, vararg roomTypes: FetchedRoomType) =
        FetchedHotel(code, name, roomTypes.toList())

    private fun roomType(code: String?, name: String?, maxOccupancy: Int?) =
        FetchedRoomType(code, name, maxOccupancy)

    private fun hotelCodes(): List<String?> =
        jdbcClient.sql("select supplier_hotel_code from hotel_mapping order by supplier_hotel_code")
            .query(String::class.java).list()

    private fun roomTypeCodes(): List<String?> =
        jdbcClient.sql("select room_type_code from room_type_mapping order by room_type_code")
            .query(String::class.java).list()

    private fun hotelId(code: String): UUID =
        jdbcClient.sql("select internal_hotel_id from hotel_mapping where supplier_hotel_code = :code")
            .param("code", code).query(UUID::class.java).single()

    private fun hotelName(code: String): String =
        jdbcClient.sql("select hotel_name from hotel_mapping where supplier_hotel_code = :code")
            .param("code", code).query(String::class.java).single()

    /** 목록에 있으면 값이 비어 있다 (ADR-0037). 비어 있는 값을 받아야 하므로 행 전체를 읽어 꺼낸다 */
    private fun missingSince(code: String): Any? =
        jdbcClient.sql("select missing_since from hotel_mapping where supplier_hotel_code = :code")
            .param("code", code)
            .query()
            .singleRow()["missing_since"]
}
