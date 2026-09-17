package com.stayaggregator.search

import com.stayaggregator.mapping.MappingRepository
import com.stayaggregator.mapping.NormalizedHotel
import com.stayaggregator.mapping.NormalizedRoomType
import com.stayaggregator.supplier.AvailabilityAdapter
import com.stayaggregator.supplier.AvailabilityRequest
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedDailyRate
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.supplier.GuestCount
import com.stayaggregator.supplier.StayPeriod
import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierResponseException
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
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 검색 한 건이 매핑 → 묶음 호출 → 판정 → 합치기로 흐르는지, 부분 실패가 응답에 드러나는지 확인한다.
 *
 * 매핑은 실제 PostgreSQL 에 넣고 (ADR-0035), 공급사는 가짜 어댑터로 대신한다. 공급사 HTTP 는 어댑터 테스트가 본다.
 */
@SpringBootTest(properties = ["stay.catalog.sync.enabled=false"])
@Import(SearchServiceIntegrationTest.Containers::class)
class SearchServiceIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Containers {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
    }

    @Autowired
    private lateinit var repository: MappingRepository

    @Autowired
    private lateinit var normalizer: AvailabilityNormalizer

    @Autowired
    private lateinit var properties: StayProperties

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private val oct5 = LocalDate.of(2026, 10, 5)
    private val oct6 = LocalDate.of(2026, 10, 6)
    private val oct7 = LocalDate.of(2026, 10, 7)
    private val period = StayPeriod(oct5, LocalDate.of(2026, 10, 8))
    private val guests = GuestCount(2, 0)

    @BeforeEach
    fun clearTables() {
        jdbcClient.sql("truncate table room_type_mapping, hotel_mapping").update()
    }

    @Test
    fun `두 공급사의 결과를 합치고 같은 숙소라도 공급사마다 요금과 조식이 다르게 나간다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "강변 호텔", roomType("R-1", "디럭스 룸", 2))))
        val a = FakeAdapter("a") { Mono.just(availability(itemA("A-1", "DLX", breakfast = false))) }
        val b = FakeAdapter("b") { Mono.just(availability(itemB("B-1", "R-1", total = 431_000, breakfast = true))) }

        val result = service(a, b).search(period, guests)

        assertThat(result.suppliers).extracting<SupplierStatus> { it.status }.containsOnly(SupplierStatus.SUCCEEDED)
        assertThat(result.roomTypes).hasSize(2)
        val fromA = result.roomTypes.single { it.roomTypeName == "디럭스" }
        val fromB = result.roomTypes.single { it.roomTypeName == "디럭스 룸" }
        assertThat(fromA.rate).isEqualTo(Rate(Money(396_000, "KRW"), RateConditions(breakfastIncluded = false)))
        assertThat(fromB.rate).isEqualTo(Rate(Money(431_000, "KRW"), RateConditions(breakfastIncluded = true)))
        assertThat(fromA.availableRooms).isEqualTo(1)
    }

    @Test
    fun `한 공급사가 실패해도 나머지 결과로 응답하고 실패한 공급사가 사유와 함께 드러난다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "한옥", roomType("R-1", "온돌", 2))))
        val a = FakeAdapter("a") { Mono.error(SupplierResponseException("공급사 a 응답을 쓸 수 없다: 503")) }
        val b = FakeAdapter("b") { Mono.just(availability(itemB("B-1", "R-1", total = 200_000, breakfast = false))) }

        val result = service(a, b).search(period, guests)

        val resultA = result.suppliers.single { it.supplierId == "a" }
        assertThat(resultA.status).isEqualTo(SupplierStatus.FAILED)
        assertThat(resultA.failureReason).contains("503")
        assertThat(resultA.roomTypes).isEmpty()
        assertThat(result.suppliers.single { it.supplierId == "b" }.status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(result.roomTypes).extracting<String> { it.roomTypeName }.containsExactly("온돌")
    }

    @Test
    fun `숙소가 50개를 넘으면 묶음으로 나눠 부른다`() {
        repository.applyCatalog("a", (1..51).map { hotel("A-$it", "숙소 $it", roomType("STD", "스탠다드", 2)) })
        val a = FakeAdapter("a") { request -> Mono.just(availability(*request.hotelCodes.map { itemA(it, "STD", breakfast = false) }.toTypedArray())) }

        val result = service(a).search(period, guests)

        assertThat(a.requests).hasSize(2)
        assertThat(a.requests.map { it.hotelCodes.size }).containsExactlyInAnyOrder(50, 1)
        assertThat(result.roomTypes).hasSize(51)
        assertThat(result.suppliers.single().failedChunks).isZero()
    }

    @Test
    fun `묶음 하나가 실패해도 다른 묶음의 항목은 나가고 실패한 묶음 수가 실린다`() {
        repository.applyCatalog("a", (1..51).map { hotel("A-$it", "숙소 $it", roomType("STD", "스탠다드", 2)) })
        val a = FakeAdapter("a") { request ->
            if (request.hotelCodes.size == 1) Mono.error(SupplierResponseException("한 묶음만 실패"))
            else Mono.just(availability(*request.hotelCodes.map { itemA(it, "STD", breakfast = false) }.toTypedArray()))
        }

        val result = service(a).search(period, guests)

        val supplier = result.suppliers.single()
        assertThat(supplier.status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(supplier.failedChunks).isEqualTo(1)
        assertThat(supplier.roomTypes).hasSize(50)
    }

    @Test
    fun `시간 한계를 넘긴 공급사만 실패가 되고 나머지는 그대로 나간다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "한옥", roomType("R-1", "온돌", 2))))
        val slow = FakeAdapter("a") { Mono.just(availability(itemA("A-1", "DLX", breakfast = false))).delayElement(Duration.ofSeconds(10)) }
        val b = FakeAdapter("b") { Mono.just(availability(itemB("B-1", "R-1", total = 200_000, breakfast = false))) }

        val result = service(slow, b, budget = Duration.ofMillis(300)).search(period, guests)

        val resultA = result.suppliers.single { it.supplierId == "a" }
        assertThat(resultA.status).isEqualTo(SupplierStatus.FAILED)
        assertThat(resultA.failureReason).contains("시간 한계")
        assertThat(result.roomTypes).extracting<String> { it.roomTypeName }.containsExactly("온돌")
    }

    @Test
    fun `스펙과 달라 뺀 건수는 실리고 매핑에 없어 뺀 것은 세지 않는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val a = FakeAdapter("a") {
            Mono.just(
                availability(
                    itemA("A-1", "DLX", breakfast = false).copy(dailyInventory = listOf(FetchedDailyInventory(oct5, 1))), // 숙박일 빠짐 → 스펙 제외
                    itemA("A-1", "NEW", breakfast = false), // 매핑에 없음
                ),
            )
        }

        val result = service(a).search(period, guests)

        val supplier = result.suppliers.single()
        assertThat(supplier.status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(supplier.roomTypes).isEmpty()
        assertThat(supplier.outOfSpecCount).isEqualTo(1)
    }

    @Test
    fun `매핑이 비어 있는 공급사는 부르지 않고 성공 0건이다`() {
        val a = FakeAdapter("a") { Mono.error(IllegalStateException("불리면 안 된다")) }

        val result = service(a).search(period, guests)

        assertThat(a.requests).isEmpty()
        assertThat(result.suppliers.single().status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(result.roomTypes).isEmpty()
    }

    @Test
    fun `검색 서비스는 재고 조회 인터페이스의 목록만 주입받는다`() {
        // 가짜가 AvailabilityAdapter 만 구현해도 서비스가 만들어진다. 목록 조회 기능을 채울 필요가 없다 (ADR-0031)
        val onlyAvailability: AvailabilityAdapter = FakeAdapter("x") { Mono.just(availability()) }

        assertThat(service(onlyAvailability)).isNotNull()
    }

    // ── 도우미 ──

    private fun service(vararg adapters: AvailabilityAdapter, budget: Duration = properties.search.budget) =
        SearchService(
            adapters.toList(),
            repository,
            normalizer,
            StayProperties(properties.suppliers, StayProperties.Search(budget, properties.search.concurrencyPerSupplier)),
        )

    private class FakeAdapter(
        override val supplierId: String,
        private val respond: (AvailabilityRequest) -> Mono<FetchedAvailability>,
    ) : AvailabilityAdapter {
        val requests = CopyOnWriteArrayList<AvailabilityRequest>()

        override fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability> {
            requests += request
            return respond(request)
        }
    }

    private fun hotel(code: String, name: String, vararg roomTypes: NormalizedRoomType) = NormalizedHotel(code, name, roomTypes.toList())
    private fun roomType(code: String, name: String, maxOccupancy: Int) = NormalizedRoomType(code, name, maxOccupancy)
    private fun availability(vararg items: FetchedAvailabilityItem) = FetchedAvailability(items.toList())

    private fun itemA(hotelCode: String, roomTypeCode: String, breakfast: Boolean) =
        FetchedAvailabilityItem(
            hotelCode, roomTypeCode, breakfast, "KRW",
            FetchedPricing.Daily(listOf(FetchedDailyRate(oct5, 110_000, 11_000), FetchedDailyRate(oct6, 140_000, 14_000), FetchedDailyRate(oct7, 110_000, 11_000))),
            listOf(FetchedDailyInventory(oct5, 3), FetchedDailyInventory(oct6, 1), FetchedDailyInventory(oct7, 5)),
        )

    private fun itemB(hotelCode: String, roomTypeCode: String, total: Long, breakfast: Boolean) =
        FetchedAvailabilityItem(
            hotelCode, roomTypeCode, breakfast, "KRW",
            FetchedPricing.Total(total, taxIncluded = true),
            listOf(FetchedDailyInventory(oct5, 4), FetchedDailyInventory(oct6, 2), FetchedDailyInventory(oct7, 6)),
        )
}
