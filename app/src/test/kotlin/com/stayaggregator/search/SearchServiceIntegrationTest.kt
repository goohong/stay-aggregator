package com.stayaggregator.search

import com.stayaggregator.domain.Money
import com.stayaggregator.domain.Rate
import com.stayaggregator.domain.RateConditions
import com.stayaggregator.mapping.MappingRepository
import com.stayaggregator.domain.NormalizedHotel
import com.stayaggregator.domain.NormalizedRoomType
import com.stayaggregator.supplier.AvailabilityAdapter
import com.stayaggregator.supplier.ResilientAvailabilityAdapters
import com.stayaggregator.supplier.SupplierCircuitBreakers
import com.stayaggregator.supplier.AvailabilityRequest
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedDailyRate
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.domain.GuestCount
import com.stayaggregator.domain.StayPeriod
import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierFailure
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * 검색 한 건이 매핑 → chunk 호출 → 정규화 → 합치기로 흐르는지, 부분 실패가 응답에 드러나는지 확인한다.
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

    @Autowired
    private lateinit var quarantine: com.stayaggregator.quarantine.QuarantineRecorder

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
        val a = FakeAdapter("a") { Mono.error(SupplierFailure.Unreadable("공급사 a 응답을 쓸 수 없다: 503")) }
        val b = FakeAdapter("b") { Mono.just(availability(itemB("B-1", "R-1", total = 200_000, breakfast = false))) }

        val result = service(a, b).search(period, guests)

        val resultA = result.suppliers.single { it.supplierId == "a" }
        assertThat(resultA.status).isEqualTo(SupplierStatus.FAILED)
        assertThat((resultA as SupplierResult.Failed).reason).contains("503")
        assertThat(resultA.roomTypes).isEmpty()
        assertThat(result.suppliers.single { it.supplierId == "b" }.status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(result.roomTypes).extracting<String> { it.roomTypeName }.containsExactly("온돌")
    }

    @Test
    fun `숙소가 50개를 넘으면 chunk 로 나눠 호출한다`() {
        repository.applyCatalog("a", (1..51).map { hotel("A-$it", "숙소 $it", roomType("STD", "스탠다드", 2)) })
        val a = FakeAdapter("a") { request -> Mono.just(availability(*request.hotelCodes.map { itemA(it, "STD", breakfast = false) }.toTypedArray())) }

        val result = service(a).search(period, guests)

        assertThat(a.requests).hasSize(2)
        assertThat(a.requests.map { it.hotelCodes.size }).containsExactlyInAnyOrder(50, 1)
        assertThat(result.roomTypes).hasSize(51)
        assertThat(result.suppliers.single().failedChunks).isZero()
    }

    @Test
    fun `chunk 하나가 실패해도 다른 chunk 의 항목은 나가고 실패한 chunk 수가 실린다`() {
        repository.applyCatalog("a", (1..51).map { hotel("A-$it", "숙소 $it", roomType("STD", "스탠다드", 2)) })
        val a = FakeAdapter("a") { request ->
            if (request.hotelCodes.size == 1) Mono.error(SupplierFailure.Unreadable("한 chunk 만 실패"))
            else Mono.just(availability(*request.hotelCodes.map { itemA(it, "STD", breakfast = false) }.toTypedArray()))
        }

        val result = service(a).search(period, guests)

        val supplier = result.suppliers.single()
        assertThat(supplier.status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(supplier.failedChunks).isEqualTo(1)
        assertThat(supplier.roomTypes).hasSize(50)
    }

    @Test
    fun `검색 전체 타임아웃을 넘긴 공급사만 실패가 되고 나머지는 그대로 나간다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "한옥", roomType("R-1", "온돌", 2))))
        val slow = FakeAdapter("a") { Mono.just(availability(itemA("A-1", "DLX", breakfast = false))).delayElement(Duration.ofSeconds(10)) }
        val b = FakeAdapter("b") { Mono.just(availability(itemB("B-1", "R-1", total = 200_000, breakfast = false))) }

        val result = service(slow, b, timeout = Duration.ofMillis(300)).search(period, guests)

        val resultA = result.suppliers.single { it.supplierId == "a" }
        assertThat(resultA.status).isEqualTo(SupplierStatus.FAILED)
        assertThat((resultA as SupplierResult.Failed).reason).contains("검색 전체 타임아웃")
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
        assertThat((supplier as SupplierResult.Succeeded).outOfSpecCount).isEqualTo(1)
    }

    @Test
    fun `매핑이 비어 있는 공급사는 호출하지 않고 성공 0건이다`() {
        val a = FakeAdapter("a") { Mono.error(IllegalStateException("호출되면 안 된다")) }

        val result = service(a).search(period, guests)

        assertThat(a.requests).isEmpty()
        assertThat(result.suppliers.single().status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(result.roomTypes).isEmpty()
    }

    // ── 재시도 (ADR-0051) ──

    @Test
    fun `일시적인 실패는 재시도하고 성공하면 그 결과를 쓴다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val attempts = AtomicInteger()
        val a = FakeAdapter("a") {
            if (attempts.incrementAndGet() <= 2) Mono.error(SupplierFailure.Unavailable("일시적"))
            else Mono.just(availability(itemA("A-1", "DLX", breakfast = false)))
        }

        val result = service(a, maxRetries = 2).search(period, guests)

        assertThat(a.requests).hasSize(3)
        assertThat(result.suppliers.single().status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(result.roomTypes).hasSize(1)
    }

    @Test
    fun `일시적이지 않은 실패는 재시도하지 않는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val a = FakeAdapter("a") { Mono.error(SupplierFailure.Rejected("잘못된 요청")) }

        val result = service(a, maxRetries = 2).search(period, guests)

        assertThat(a.requests).hasSize(1)
        assertThat(result.suppliers.single().status).isEqualTo(SupplierStatus.FAILED)
    }

    @Test
    fun `재시도해도 계속 실패하면 정한 횟수에서 멈추고 원래 실패로 나간다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val a = FakeAdapter("a") { Mono.error(SupplierFailure.Unavailable("공급사 a 가 503 을 알렸다")) }

        val result = service(a, maxRetries = 2).search(period, guests)

        // 첫 호출 + 재시도 2회
        assertThat(a.requests).hasSize(3)
        val supplier = result.suppliers.single()
        assertThat(supplier.status).isEqualTo(SupplierStatus.FAILED)
        // 다른 예외로 감싸지 않고 원래 사유가 그대로 나간다
        assertThat((supplier as SupplierResult.Failed).reason).isEqualTo("공급사 a 가 503 을 알렸다")
    }

    @Test
    fun `요청 한도 초과는 한 번만 재시도하고 최소 백오프 1초 이상 기다린다`() {
        // 5xx 기준(2회)이 아니라 요청 한도 초과 기준(1회, 1초)을 쓴다 (ADR-0068)
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val calledAt = CopyOnWriteArrayList<Long>()
        val a = FakeAdapter("a") {
            calledAt += System.nanoTime()
            Mono.error(SupplierFailure.Throttled("공급사 a 429"))
        }
        val throttled = StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(1_500))

        val result = service(a, timeout = Duration.ofSeconds(4), maxRetries = 2, throttledRetry = throttled).search(period, guests)

        assertThat(a.requests).hasSize(2)
        assertThat(Duration.ofNanos(calledAt[1] - calledAt[0])).isGreaterThanOrEqualTo(Duration.ofSeconds(1))
        assertThat((result.suppliers.single() as SupplierResult.Failed).reason).isEqualTo("공급사 a 429")
    }

    @Test
    fun `재시도 중 실패 종류가 바뀌면 만난 종류 중 가장 적은 재시도 횟수에서 멈춘다`() {
        // 503 뒤 429: 5xx 한도는 2회지만 요청 한도 초과 한도 1회에서 멈춘다. 최악의 시간이 종류별 최악을 넘지 않게 한다 (ADR-0068 의 (A))
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val attempts = AtomicInteger()
        val a = FakeAdapter("a") {
            if (attempts.incrementAndGet() == 1) Mono.error(SupplierFailure.Unavailable("공급사 a 503"))
            else Mono.error(SupplierFailure.Throttled("공급사 a 429"))
        }

        service(a, maxRetries = 2).search(period, guests)

        assertThat(a.requests).hasSize(2)
    }

    @Test
    fun `재시도를 0 으로 두면 한 번만 호출한다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val a = FakeAdapter("a") { Mono.error(SupplierFailure.Unavailable("일시적")) }

        service(a, maxRetries = 0).search(period, guests)

        assertThat(a.requests).hasSize(1)
    }

    @Test
    fun `스펙과 달라 뺀 항목은 격리 기록에 남는다`() {
        jdbcClient.sql("truncate table quarantine_record").update()
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val a = FakeAdapter("a") { Mono.just(availability(itemA("A-1", "DLX", breakfast = false).copy(currency = null))) }

        service(a).search(period, guests)

        // 비동기로 기록되므로 잠깐 기다린다 (ADR-0055)
        val deadline = System.currentTimeMillis() + 5_000
        fun rows() = jdbcClient.sql("select excluded_value from quarantine_record").query(String::class.java).list()
        while (rows().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertThat(rows()).containsExactly("CURRENCY")
    }

    // ── 지표 (ADR-0060) ──

    @Test
    fun `chunk 호출마다 공급사와 결과로 나눠 센다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "한옥", roomType("R-1", "온돌", 2))))
        val a = FakeAdapter("a") { Mono.just(availability(itemA("A-1", "DLX", breakfast = false))) }
        val b = FakeAdapter("b") { Mono.error(SupplierFailure.Rejected("공급사 b 503")) }
        val registry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()

        service(a, b, meterRegistry = registry).search(period, guests)

        fun count(supplier: String, outcome: String) =
            registry.find("stay.supplier.availability").tags("supplier", supplier, "outcome", outcome).timer()?.count() ?: 0L
        assertThat(count("a", "success")).isEqualTo(1)
        assertThat(count("b", "supplier_failure")).isEqualTo(1)
    }

    // ── 서킷 브레이커 (ADR-0056) ── 테스트 설정은 최근 chunk 4개 중 50% 실패면 연다

    @Test
    fun `공급사가 계속 실패하면 서킷이 열리고 그 뒤 검색은 그 공급사를 호출하지 않는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        repository.applyCatalog("b", listOf(hotel("B-1", "한옥", roomType("R-1", "온돌", 2))))
        val a = FakeAdapter("a") { Mono.error(SupplierFailure.Unavailable("공급사 a 503")) }
        val b = FakeAdapter("b") { Mono.just(availability(itemB("B-1", "R-1", total = 200_000, breakfast = false))) }
        val breakers = breakers()
        val service = service(a, b, breakers = breakers)

        repeat(4) { service.search(period, guests) }
        val callsBeforeOpen = a.requests.size
        val result = service.search(period, guests)

        assertThat(breakers.of("a").state).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN)
        // 열린 뒤에는 호출하지 않는다
        assertThat(a.requests).hasSize(callsBeforeOpen)
        val resultA = result.suppliers.single { it.supplierId == "a" }
        assertThat(resultA.status).isEqualTo(SupplierStatus.FAILED)
        assertThat((resultA as SupplierResult.Failed).reason).contains("서킷이 열려 있어")
        // 다른 공급사는 그대로 나간다
        assertThat(result.suppliers.single { it.supplierId == "b" }.status).isEqualTo(SupplierStatus.SUCCEEDED)
        assertThat(breakers.of("b").state).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED)
    }

    @Test
    fun `재시도 끝에 성공한 chunk 는 서킷이 실패로 세지 않는다`() {
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val attempts = AtomicInteger()
        val a = FakeAdapter("a") {
            if (attempts.incrementAndGet() % 2 == 1) Mono.error(SupplierFailure.Unavailable("일시적"))
            else Mono.just(availability(itemA("A-1", "DLX", breakfast = false)))
        }
        val breakers = breakers()

        repeat(4) { service(a, maxRetries = 1, breakers = breakers).search(period, guests) }

        val metrics = breakers.of("a").metrics
        assertThat(metrics.numberOfSuccessfulCalls).isEqualTo(4)
        assertThat(metrics.numberOfFailedCalls).isZero()
    }

    @Test
    fun `공급사 실패가 아닌 내부 오류는 서킷이 성공으로도 실패로도 세지 않는다`() {
        // 내부 오류 때문에 정상인 공급사를 차단하지 않는다 (ADR-0056)
        repository.applyCatalog("a", listOf(hotel("A-1", "강변 호텔", roomType("DLX", "디럭스", 2))))
        val a = FakeAdapter("a") { Mono.error(IllegalStateException("내부 오류")) }
        val breakers = breakers()

        repeat(6) { service(a, breakers = breakers).search(period, guests) }

        assertThat(breakers.of("a").state).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED)
        assertThat(a.requests).hasSize(6)
        // 무시해야 한다. 성공으로 세면 실패율이 희석된다
        assertThat(breakers.of("a").metrics.numberOfSuccessfulCalls).isZero()
        assertThat(breakers.of("a").metrics.numberOfFailedCalls).isZero()
    }

    @Test
    fun `검색 서비스는 재고 조회 인터페이스의 목록만 주입받는다`() {
        // 가짜가 AvailabilityAdapter 만 구현해도 서비스가 만들어진다. 목록 조회 기능을 채울 필요가 없다 (ADR-0031)
        val onlyAvailability: AvailabilityAdapter = FakeAdapter("x") { Mono.just(availability()) }

        assertThat(service(onlyAvailability)).isNotNull()
    }

    // ── 도우미 ──

    /**
     * 테스트마다 서킷을 새로 만든다. 앞 테스트의 실패가 서킷에 남아 다음 테스트를 막지 않게 한다.
     * 서킷을 직접 보고 싶은 테스트는 [breakers] 로 넘긴다.
     */
    private fun service(
        vararg adapters: AvailabilityAdapter,
        timeout: Duration = properties.search.timeout,
        maxRetries: Int = 0,
        throttledRetry: StayProperties.RetryPolicy = properties.search.throttledRetry,
        breakers: SupplierCircuitBreakers? = null,
        meterRegistry: io.micrometer.core.instrument.MeterRegistry = io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
    ): SearchService {
        // 재시도까지 다 쓴 chunk 가 검색 전체 타임아웃보다 먼저 끝나야 한다(StayProperties 가 지킴). 검색 전체 타임아웃을 줄인 테스트는 호출 타임아웃도 함께 줄인다.
        // 가짜 어댑터는 이 값을 쓰지 않는다
        val suppliers = properties.suppliers.mapValues { (_, s) ->
            if (timeout >= properties.search.timeout) s else s.copy(availabilityTimeout = timeout.dividedBy(8), connectTimeout = timeout.dividedBy(16))
        }
        val props = StayProperties(suppliers, properties.search.copy(timeout = timeout, retry = properties.search.retry.copy(maxRetries = maxRetries), throttledRetry = throttledRetry))
        val wrapped = ResilientAvailabilityAdapters.wrap(adapters.toList(), breakers ?: SupplierCircuitBreakers(props), com.stayaggregator.supplier.SupplierCallMetrics(meterRegistry), props.search)
        return SearchService(wrapped, repository, normalizer, quarantine, props)
    }

    private fun breakers() = SupplierCircuitBreakers(properties)

    private class FakeAdapter(
        override val supplierId: String,
        private val respond: (AvailabilityRequest) -> Mono<FetchedAvailability>,
    ) : AvailabilityAdapter {
        val requests = CopyOnWriteArrayList<AvailabilityRequest>()

        /**
         * `defer` 로 감싸 **구독할 때마다** 호출 한 번으로 센다.
         * 재시도는 같은 `Mono` 를 다시 구독하는 것이라, 밖에서 한 번만 세면 재시도가 보이지 않는다.
         * 실제 어댑터의 WebClient 체인도 다시 구독하면 실제로 다시 호출한다.
         */
        override fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability> =
            Mono.defer {
                requests += request
                respond(request)
            }
    }

    private fun hotel(code: String, name: String, vararg roomTypes: NormalizedRoomType) = NormalizedHotel(code, name, roomTypes.toList())
    private fun roomType(code: String, name: String, maxOccupancy: Int) = NormalizedRoomType(code, name, maxOccupancy)
    private fun availability(vararg items: FetchedAvailabilityItem) = FetchedAvailability(items.toList())

    private fun itemA(hotelCode: String, roomTypeCode: String, breakfast: Boolean) =
        FetchedAvailabilityItem(
            hotelCode, roomTypeCode, breakfastIncluded = breakfast, currency = "KRW",
            pricing = FetchedPricing.Daily(listOf(FetchedDailyRate(oct5, 110_000, 11_000), FetchedDailyRate(oct6, 140_000, 14_000), FetchedDailyRate(oct7, 110_000, 11_000))),
            dailyInventory = listOf(FetchedDailyInventory(oct5, 3), FetchedDailyInventory(oct6, 1), FetchedDailyInventory(oct7, 5)),
        )

    private fun itemB(hotelCode: String, roomTypeCode: String, total: Long, breakfast: Boolean) =
        FetchedAvailabilityItem(
            hotelCode, roomTypeCode, breakfastIncluded = breakfast, currency = "KRW",
            pricing = FetchedPricing.Total(total, taxIncluded = true),
            dailyInventory = listOf(FetchedDailyInventory(oct5, 4), FetchedDailyInventory(oct6, 2), FetchedDailyInventory(oct7, 6)),
        )
}
