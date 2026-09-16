package com.stayaggregator.catalog

import com.stayaggregator.mapping.AppliedCatalog
import com.stayaggregator.mapping.MappingRepository
import com.stayaggregator.mapping.NormalizedHotel
import com.stayaggregator.mapping.NormalizedRoomType
import com.stayaggregator.supplier.CatalogAdapter
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.SupplierResponseException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataAccessException
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
    fun `반영 결과는 목록에 없어 표시가 남은 숙소만 센다`() {
        val two = catalog(
            hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)),
            hotel("A-2", "한옥 스테이", roomType("ONDOL", "온돌", 2)),
        )
        assertThat(apply(two).missingHotels).isZero()

        // 같은 목록을 다시 받으면, 반영 도중 전부 표시했다가 되살리므로 남는 것이 없다
        assertThat(apply(two).missingHotels).isZero()

        val one = catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)))
        assertThat(apply(one).missingHotels).isEqualTo(1)
    }

    @Test
    fun `응답을 읽지 못한 동기화는 기존 표시를 건드리지 않는다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val before = hotelId("A-1")

        syncWith(FakeCatalogAdapter("a", Mono.error(SupplierResponseException("읽을 수 없는 응답"))))

        // 표시했다가 되살리는 순서라, 읽지 못한 동기화가 표시만 남기고 끝나면 있는 숙소가 사라진 것이 된다 (ADR-0037)
        assertThat(missingSince("A-1")).isNull()
        assertThat(hotelId("A-1")).isEqualTo(before)
    }

    @Test
    fun `숙소는 남고 객실 타입만 빠지면 그 객실 타입에만 사라진 시각이 찍힌다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2), roomType("STD", "스탠다드", 2))))

        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))

        assertThat(missingSince("A-1")).isNull()
        assertThat(roomTypeMissingSince("DLX")).isNull()
        assertThat(roomTypeMissingSince("STD")).isNotNull()
    }

    @Test
    fun `숙소가 빠지면 그 숙소의 객실 타입도 표시되고 다시 나타나면 같은 식별자로 풀린다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val before = roomTypeId("DLX")

        sync(catalog())
        assertThat(roomTypeMissingSince("DLX")).isNotNull()

        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        assertThat(roomTypeMissingSince("DLX")).isNull()
        assertThat(roomTypeId("DLX")).isEqualTo(before)
    }

    @Test
    fun `저장 도중 실패하면 그 공급사 매핑이 이전 상태로 남는다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val before = hotelId("A-1")

        // 반영은 먼저 모두 표시하고 되살리는 순서라, 중간에 멈추면 있는 숙소가 사라진 것으로 남을 수 있다.
        // DB 가 받지 못하는 값으로 실패를 만들어 트랜잭션이 통째로 취소되는지 본다 (ADR-0038).
        // 실패하는 숙소 앞에 정상 숙소를 두어, 이미 저장된 것까지 되돌려지는지도 함께 본다.
        val rejected = listOf(
            NormalizedHotel("A-2", "한옥 스테이", listOf(NormalizedRoomType("ONDOL", "온돌", 2))),
            NormalizedHotel("A-3", "널 문자\u0000가 든 이름", listOf(NormalizedRoomType("STD", "스탠다드", 2))),
        )
        assertThatThrownBy { repository.applyCatalog("a", rejected) }
            .isInstanceOf(DataAccessException::class.java)

        assertThat(hotelCodes()).containsExactly("A-1")
        assertThat(missingSince("A-1")).isNull()
        assertThat(hotelId("A-1")).isEqualTo(before)
    }

    @Test
    fun `공급사를 하나 더 붙여도 소비자 코드를 고치지 않는다`() {
        syncWith(
            FakeCatalogAdapter("a", Mono.just(FetchedCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)))))),
            FakeCatalogAdapter("b", Mono.just(FetchedCatalog("b", listOf(hotel("B-1", "한옥 스테이", roomType("ONDOL", "온돌", 2)))))),
            FakeCatalogAdapter("c", Mono.just(FetchedCatalog("c", listOf(hotel("C-1", "바다 리조트", roomType("SUITE", "스위트", 4)))))),
        )

        assertThat(hotelCodes()).containsExactly("A-1", "B-1", "C-1")
    }

    @Test
    fun `공급사 하나가 실패해도 다른 공급사는 반영된다`() {
        val failing = FakeCatalogAdapter("a", Mono.error(SupplierResponseException("읽을 수 없는 응답")))
        val working = FakeCatalogAdapter("b", Mono.just(FetchedCatalog("b", listOf(hotel("B-1", "한옥 스테이", roomType("ONDOL", "온돌", 2))))))

        CatalogSyncService(listOf(failing, working), normalizer, repository).syncAll()

        assertThat(hotelCodes()).containsExactly("B-1")
    }

    @Test
    fun `저장 단계에서 실패해도 다른 공급사는 반영된다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))

        // 정규화는 통과하고 저장에서 DB 가 거부한다. 조회 실패가 아니라 저장 실패일 때도
        // 그 공급사만 건너뛰는지 본다 (ADR-0019, ADR-0038).
        val rejected = FakeCatalogAdapter(
            "a",
            Mono.just(FetchedCatalog("a", listOf(hotel("A-2", "널 문자\u0000가 든 이름", roomType("STD", "스탠다드", 2))))),
        )
        val working = FakeCatalogAdapter("b", Mono.just(FetchedCatalog("b", listOf(hotel("B-1", "한옥 스테이", roomType("ONDOL", "온돌", 2))))))

        syncWith(rejected, working)

        assertThat(hotelCodes()).containsExactly("A-1", "B-1")
        assertThat(missingSince("A-1")).isNull()
    }

    @Test
    fun `공급사가 알린 실패가 아니어도 다른 공급사는 반영된다`() {
        // 우리 쪽 결함으로 나는 예외다. 공급사 실패와 다른 가지를 타지만 건너뛰는 것은 같다 (ADR-0019)
        val broken = FakeCatalogAdapter("a", Mono.error(IllegalStateException("어댑터 안에서 난 오류")))
        val working = FakeCatalogAdapter("b", Mono.just(FetchedCatalog("b", listOf(hotel("B-1", "한옥 스테이", roomType("ONDOL", "온돌", 2))))))

        syncWith(broken, working)

        assertThat(hotelCodes()).containsExactly("B-1")
    }

    @Test
    fun `값을 정할 수 없어 뺀 숙소도 목록에 없는 것으로 표시된다`() {
        sync(catalog(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))

        // 이름이 없어 정규화에서 빠진다. 공급사가 뺀 것인지 우리가 읽지 못한 것인지는 구분하지 않는다 (ADR-0037, ADR-0039)
        sync(catalog(hotel("A-1", null, roomType("DLX", "디럭스", 2))))

        assertThat(missingSince("A-1")).isNotNull()
    }

    private fun apply(catalog: FetchedCatalog): AppliedCatalog {
        val normalized = normalizer.normalize(catalog)
        return repository.applyCatalog(normalized.supplierId, normalized.hotels)
    }

    private fun sync(catalog: FetchedCatalog) {
        syncWith(FakeCatalogAdapter(catalog.supplierId, Mono.just(catalog)))
    }

    /** 소비자는 어댑터 목록을 주입받기만 한다. 공급사가 늘어도 이 호출은 그대로다 (ADR-0031) */
    private fun syncWith(vararg adapters: CatalogAdapter) {
        CatalogSyncService(adapters.toList(), normalizer, repository).syncAll()
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

    private fun roomTypeMissingSince(code: String): Any? =
        jdbcClient.sql("select missing_since from room_type_mapping where room_type_code = :code")
            .param("code", code)
            .query()
            .singleRow()["missing_since"]

    private fun roomTypeId(code: String): UUID =
        jdbcClient.sql("select internal_room_type_id from room_type_mapping where room_type_code = :code")
            .param("code", code).query(UUID::class.java).single()
}
