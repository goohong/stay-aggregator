package com.stayaggregator.supplier

import com.stayaggregator.catalog.SupplierResponseException
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

        assertThatThrownBy { adapterA().fetchCatalog().block() }
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
    fun `공급사가 응답하지 않으면 정한 시간에 끊는다`() {
        server.createContext("/a/v1/hotels") { exchange ->
            Thread.sleep(2_000)
            write(exchange, 200, "{}")
        }

        assertThatThrownBy { adapterA(timeout = Duration.ofMillis(200)).fetchCatalog().block() }
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
