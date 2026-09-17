package com.stayaggregator.supplier

import com.stayaggregator.supplier.a.SupplierACatalogAdapter
import com.stayaggregator.supplier.b.SupplierBCatalogAdapter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * 어댑터가 실제 HTTP 응답을 받아 공통 형태로 바꾸는지, 공급사마다 다른 실패 표현을 같은 신호로 바꾸는지 확인한다.
 *
 * 공급사는 JDK 에 들어 있는 작은 HTTP 서버로 흉내 낸다. Mock 모듈은 별도 프로세스라(ADR-0005)
 * 테스트에서 띄우지 않고, 여기서는 어댑터 하나만 본다.
 */
class SupplierCatalogAdapterTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

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
    fun `공급사 A 의 응답을 공통 형태로 바꾼다`() {
        respond(
            "/a/v1/hotels",
            status = 200,
            body = """
                {"items":[{"hotelCode":"A-1","hotelName":"강변 호텔",
                "roomTypes":[{"roomTypeCode":"DLX","roomTypeName":"디럭스","maxOccupancy":2}]}]}
            """.trimIndent(),
        )

        val fetched = adapterA().fetchCatalog().block()!!

        assertThat(fetched.supplierId).isEqualTo("a")
        assertThat(fetched.hotels).singleElement()
            .satisfies({ hotel ->
                assertThat(hotel.code).isEqualTo("A-1")
                assertThat(hotel.roomTypes).singleElement()
                    .satisfies({ roomType -> assertThat(roomType.maxOccupancy).isEqualTo(2) })
            })
    }

    @Test
    fun `공급사 A 가 HTTP 실패를 주면 오류가 된다`() {
        respond("/a/v1/hotels", status = 503, body = """{"error":"SERVICE_UNAVAILABLE","message":"temporarily unavailable"}""")

        // B 가 본문 결과 코드로 알린 실패와 같은 신호가 되어야 한다 (ADR-0027 의 첫 질문, ADR-0031)
        assertThatThrownBy { adapterA().fetchCatalog().block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessageContaining("503")
    }

    @Test
    fun `공급사 B 가 본문 결과 코드로 알린 실패도 같은 오류가 된다`() {
        respond("/b/api/properties", status = 200, body = """{"resultCode":"E503","resultMessage":"TEMPORARILY_UNAVAILABLE","data":null}""")

        assertThatThrownBy { adapterB().fetchCatalog().block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessageContaining("E503")
    }

    @Test
    fun `스펙에 없는 필드가 섞여 와도 무시한다`() {
        respond(
            "/a/v1/hotels",
            status = 200,
            body = """
                {"items":[{"hotelCode":"A-1","hotelName":"강변 호텔","starRating":5,
                "roomTypes":[{"roomTypeCode":"DLX","roomTypeName":"디럭스","maxOccupancy":2,"bedType":"KING"}]}],
                "page":1}
            """.trimIndent(),
        )

        val fetched = adapterA().fetchCatalog().block()!!

        assertThat(fetched.hotels).singleElement()
            .satisfies({ hotel -> assertThat(hotel.code).isEqualTo("A-1") })
    }

    @Test
    fun `목록 필드가 없는 응답은 값을 거르지 않고 없는 대로 넘긴다`() {
        // 판정은 정규화가 한다. 어댑터가 빈 목록으로 바꿔 버리면 그 신호가 사라진다 (ADR-0041)
        respond("/a/v1/hotels", status = 200, body = "{}")

        val fetched = adapterA().fetchCatalog().block()!!

        assertThat(fetched.hotels).isNull()
    }

    @Test
    fun `숙소 안의 객실 타입 목록도 없는 대로 넘긴다`() {
        respond("/a/v1/hotels", status = 200, body = """{"items":[{"hotelCode":"A-1","hotelName":"강변 호텔"}]}""")

        val fetched = adapterA().fetchCatalog().block()!!

        assertThat(fetched.hotels).singleElement()
            .satisfies({ hotel -> assertThat(hotel.roomTypes).isNull() })
    }

    @Test
    fun `본문을 읽을 수 없으면 오류가 된다`() {
        // 역직렬화에서 실패하는 것은 JSON 문법 오류와 타입 불일치뿐이고, 둘 다 공급사 실패로 본다 (ADR-0030, ADR-0027 의 첫 질문)
        respond("/a/v1/hotels", status = 200, body = """{"items":[{"hotelCode":""")

        assertThatThrownBy { adapterA().fetchCatalog().block() }
            .isInstanceOf(SupplierResponseException::class.java)
    }

    @Test
    fun `공급사가 응답하지 않으면 정한 시간에 끊는다`() {
        server.createContext("/a/v1/hotels") { exchange ->
            Thread.sleep(2_000)
            write(exchange, 200, "{}")
        }

        assertThatThrownBy { adapterA(timeout = Duration.ofMillis(200)).fetchCatalog().block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasRootCauseInstanceOf(TimeoutException::class.java)
    }

    private fun adapterA(timeout: Duration = Duration.ofSeconds(1)) =
        SupplierACatalogAdapter(properties("a", timeout))

    private fun adapterB(timeout: Duration = Duration.ofSeconds(1)) =
        SupplierBCatalogAdapter(properties("b", timeout))

    private fun properties(supplierId: String, timeout: Duration) =
        StayProperties(
            mapOf(
                supplierId to StayProperties.Supplier(
                    baseUrl = "http://localhost:$port",
                    apiKey = "test",
                    timeout = timeout,
                ),
            ),
        )

    private fun respond(path: String, status: Int, body: String) {
        server.createContext(path) { exchange -> write(exchange, status, body) }
    }

    private fun write(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
