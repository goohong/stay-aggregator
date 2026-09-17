package com.stayaggregator.mapping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 검색이 매핑에서 읽는 것이 "지금 공급사 목록에 있는 것" 뿐인지 확인한다 (ADR-0037).
 *
 * 매핑은 동기화의 저장 경로로 넣고, 동기화가 두 번째 목록에서 뺀 것이 읽기에서 빠지는지 본다.
 */
@SpringBootTest(properties = ["stay.catalog.sync.enabled=false"])
@Import(MappingReadIntegrationTest.Containers::class)
class MappingReadIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Containers {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
    }

    @Autowired
    private lateinit var repository: MappingRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @BeforeEach
    fun clearTables() {
        jdbcClient.sql("truncate table room_type_mapping, hotel_mapping").update()
    }

    @Test
    fun `숙소와 객실 타입을 내부 식별자와 함께 읽는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2), roomType("STD", "스탠다드", 3))))

        val hotels = repository.findActiveHotels("a")

        assertThat(hotels).singleElement().satisfies({ hotel ->
            assertThat(hotel.supplierHotelCode).isEqualTo("A-1")
            assertThat(hotel.name).isEqualTo("강변 호텔")
            assertThat(hotel.roomTypes).extracting<String> { it.roomTypeCode }.containsExactly("DLX", "STD")
            assertThat(hotel.roomTypes.map { it.maxOccupancy }).containsExactly(2, 3)
        })
    }

    @Test
    fun `missing_since 가 찍힌 숙소는 읽지 않는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2)), hotel("A-2", "한옥", roomType("ONDOL", "온돌", 2))))
        repository.applyCatalog("a", listOf(hotel("A-2", "한옥", roomType("ONDOL", "온돌", 2))))

        val hotels = repository.findActiveHotels("a")

        assertThat(hotels).extracting<String> { it.supplierHotelCode }.containsExactly("A-2")
    }

    @Test
    fun `missing_since 가 찍힌 객실 타입만 빠지고 숙소는 남는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2), roomType("STD", "스탠다드", 3))))
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))

        val hotels = repository.findActiveHotels("a")

        assertThat(hotels).singleElement().satisfies({ hotel ->
            assertThat(hotel.roomTypes).extracting<String> { it.roomTypeCode }.containsExactly("DLX")
        })
    }

    @Test
    fun `다른 공급사의 매핑은 읽지 않는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "강변 호텔", roomType("R-1", "디럭스 룸", 2))))

        assertThat(repository.findActiveHotels("a")).extracting<String> { it.supplierHotelCode }.containsExactly("A-1")
        assertThat(repository.findActiveHotels("b")).extracting<String> { it.supplierHotelCode }.containsExactly("B-1")
    }

    @Test
    fun `매핑이 없는 공급사는 빈 목록이다`() {
        assertThat(repository.findActiveHotels("c")).isEmpty()
    }

    private fun hotel(code: String, name: String, vararg roomTypes: NormalizedRoomType) =
        NormalizedHotel(code, name, roomTypes.toList())

    private fun roomType(code: String, name: String, maxOccupancy: Int) =
        NormalizedRoomType(code, name, maxOccupancy)
}
