package com.stayaggregator.supplier

import com.stayaggregator.supplier.a.SupplierAAdapter
import com.stayaggregator.supplier.b.SupplierBAdapter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.time.LocalDate

/**
 * 재고·요금 경계의 어댑터가 공급사마다 다른 요금 모양을 판정 없이 그대로 넘기는지,
 * 요청 파라미터가 스펙대로 나가는지 확인한다 (ADR-0031, ADR-0049).
 *
 * 실패 표현의 통일은 [SupplierCatalogAdapterTest] 가 목록 경계로 확인했고, 두 경계가 같은 감싸기를 쓰므로 여기서는 B 의 결과 코드 하나만 다시 본다.
 */
class SupplierAvailabilityAdapterTest {

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var lastQuery: String? = null

    private val request = AvailabilityRequest(
        hotelCodes = listOf("A-1", "A-2"),
        period = StayPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8)),
        guests = GuestCount(adults = 2, children = 1),
    )

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `공급사 A 의 날짜별 요금과 재고를 갈라서 그대로 넘긴다`() {
        respond(
            "/a/v1/availability",
            status = 200,
            body = """
                {"items":[{"hotelCode":"A-1","hotelName":"강변 호텔","roomTypeCode":"DLX","roomTypeName":"디럭스","maxOccupancy":2,
                "breakfastIncluded":false,"currency":"KRW",
                "dailyRates":[{"date":"2026-10-05","remainingRooms":4,"nightlyRate":110000,"taxAmount":11000},
                              {"date":"2026-10-06","remainingRooms":2,"nightlyRate":140000,"taxAmount":14000}]}]}
            """.trimIndent(),
        )

        val fetched = adapterA().fetchAvailability(request).block()!!

        assertThat(fetched.items).singleElement().satisfies({ item ->
            assertThat(item.hotelCode).isEqualTo("A-1")
            assertThat(item.roomTypeCode).isEqualTo("DLX")
            assertThat(item.breakfastIncluded).isFalse()
            assertThat(item.currency).isEqualTo("KRW")
            // 더하지 않는다. 날짜별 그대로다
            assertThat(item.pricing).isEqualTo(
                FetchedPricing.Daily(
                    listOf(
                        FetchedDailyRate(LocalDate.of(2026, 10, 5), 110_000, 11_000),
                        FetchedDailyRate(LocalDate.of(2026, 10, 6), 140_000, 14_000),
                    ),
                ),
            )
            assertThat(item.dailyInventory).containsExactly(
                FetchedDailyInventory(LocalDate.of(2026, 10, 5), 4),
                FetchedDailyInventory(LocalDate.of(2026, 10, 6), 2),
            )
        })
    }

    @Test
    fun `공급사 A 에 숙소 코드 묶음과 날짜와 인원을 스펙 이름으로 보낸다`() {
        respond("/a/v1/availability", status = 200, body = """{"items":[]}""")

        adapterA().fetchAvailability(request).block()

        assertThat(lastQuery).isEqualTo("hotelCodes=A-1,A-2&checkIn=2026-10-05&checkOut=2026-10-08&adults=2&children=1")
    }

    @Test
    fun `공급사 B 의 총액 요금을 그대로 넘긴다`() {
        respond(
            "/b/api/search",
            status = 200,
            body = """
                {"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[
                  {"propertyId":"B-1","propertyName":"강변 호텔","roomId":"R-1","roomName":"디럭스 룸","maxOccupancy":2,
                   "breakfastIncluded":true,"currency":"KRW","totalPrice":431000,"taxIncluded":true,
                   "inventory":[{"date":"2026-10-05","remainingRooms":4}]}]}}
            """.trimIndent(),
        )

        val fetched = adapterB().fetchAvailability(request).block()!!

        assertThat(fetched.items).singleElement().satisfies({ item ->
            assertThat(item.hotelCode).isEqualTo("B-1")
            assertThat(item.roomTypeCode).isEqualTo("R-1")
            assertThat(item.breakfastIncluded).isTrue()
            assertThat(item.pricing).isEqualTo(FetchedPricing.Total(totalPrice = 431_000, taxIncluded = true))
            assertThat(item.dailyInventory).containsExactly(FetchedDailyInventory(LocalDate.of(2026, 10, 5), 4))
        })
    }

    @Test
    fun `공급사 B 는 숙소 코드 파라미터 이름이 다르다`() {
        respond("/b/api/search", status = 200, body = """{"resultCode":"0000","resultMessage":"OK","data":{"items":[]}}""")

        adapterB().fetchAvailability(request).block()

        assertThat(lastQuery).startsWith("propertyIds=A-1,A-2&")
    }

    @Test
    fun `공급사 B 가 본문 결과 코드로 알린 실패는 목록 경계와 같은 오류가 된다`() {
        respond("/b/api/search", status = 200, body = """{"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}""")

        assertThatThrownBy { adapterB().fetchAvailability(request).block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessageContaining("E503")
    }

    // ── 다시 불러 볼 여지가 있는 실패인지 (ADR-0051) ──

    @Test
    fun `공급사가 5xx 로 알린 실패는 다시 불러 볼 여지가 있다`() {
        respond("/a/v1/availability", status = 503, body = """{"error":"SERVICE_UNAVAILABLE"}""")

        assertThat(transientOf { adapterA().fetchAvailability(request).block() }).isTrue()
    }

    @Test
    fun `호출 한도 초과도 여지가 있다`() {
        respond("/a/v1/availability", status = 429, body = """{"error":"RATE_LIMIT_EXCEEDED"}""")

        assertThat(transientOf { adapterA().fetchAvailability(request).block() }).isTrue()
    }

    @Test
    fun `잘못된 요청은 여지가 없다`() {
        // 같은 요청을 다시 보내면 같은 거절이다
        respond("/a/v1/availability", status = 400, body = """{"error":"TOO_MANY_HOTEL_CODES"}""")

        assertThat(transientOf { adapterA().fetchAvailability(request).block() }).isFalse()
    }

    @Test
    fun `정한 시간 안에 오지 않은 것은 여지가 없다`() {
        // 느리다는 신호라 다시 불러도 느릴 가능성이 높다 (ADR-0051)
        server.createContext("/a/v1/availability") { exchange ->
            Thread.sleep(2_000)
            write(exchange, 200, """{"items":[]}""")
        }

        assertThat(transientOf { adapterA().fetchAvailability(request).block() }).isFalse()
    }

    @Test
    fun `연결을 거절당한 것은 여지가 있다`() {
        // 아무도 듣지 않는 포트. 공급사 재시작 같은 순간적 상황일 수 있다 (ADR-0051)
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        val adapter = SupplierAAdapter(properties("a").let { p -> StayProperties(p.suppliers.mapValues { (_, s) -> s.copy(baseUrl = "http://localhost:$closedPort") }, p.search) })

        assertThat(transientOf { adapter.fetchAvailability(request).block() }).isTrue()
    }

    @Test
    fun `공급사 B 의 결과 코드도 갈린다`() {
        respond("/b/api/search", status = 200, body = """{"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}""")
        assertThat(transientOf { adapterB().fetchAvailability(request).block() }).isTrue()

        server.removeContext("/b/api/search")
        respond("/b/api/search", status = 200, body = """{"resultCode":"E400","resultMessage":"BAD_REQUEST","data":null}""")
        assertThat(transientOf { adapterB().fetchAvailability(request).block() }).isFalse()
    }

    @Test
    fun `항목 목록 필드가 없으면 없는 대로 넘긴다`() {
        // 판정은 어댑터 밖에서 한다 (ADR-0041)
        respond("/a/v1/availability", status = 200, body = "{}")

        val fetched = adapterA().fetchAvailability(request).block()!!

        assertThat(fetched.items).isNull()
    }

    /** 던져진 공급사 실패가 다시 불러 볼 여지가 있다고 말하는지 */
    private fun transientOf(call: () -> Unit): Boolean {
        val thrown = catchThrowable { call() }
        assertThat(thrown).isInstanceOf(SupplierResponseException::class.java)
        return (thrown as SupplierResponseException).transient
    }

    private fun adapterA() = SupplierAAdapter(properties("a"))

    private fun adapterB() = SupplierBAdapter(properties("b"))

    private fun properties(supplierId: String) =
        StayProperties(
            suppliers = mapOf(
                supplierId to StayProperties.Supplier(
                    baseUrl = "http://localhost:$port",
                    apiKey = "test",
                    timeout = Duration.ofSeconds(1),
                    availabilityTimeout = Duration.ofSeconds(1),
                ),
            ),
            search = StayProperties.Search(
                budget = Duration.ofSeconds(2),
                concurrencyPerSupplier = 4,
                maxRetries = 0,
                retryMinBackoff = Duration.ofMillis(10),
                retryMaxBackoff = Duration.ofMillis(50),
                circuitBreaker = StayProperties.CircuitBreaker(50f, 4, 4, Duration.ofSeconds(1), 1),
            ),
        )

    private fun respond(path: String, status: Int, body: String) {
        server.createContext(path) { exchange ->
            lastQuery = exchange.requestURI.rawQuery
            write(exchange, status, body)
        }
    }

    private fun write(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
