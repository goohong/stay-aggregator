package com.stayaggregator.mocksupplier

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 공급사 A·B 를 흉내낸다. 요청 파라미터는 보지 않고 [SupplierFixtures] 의 고정 응답을 준다.
 *
 * 재고·요금 조회만 모드를 바꿀 수 있다.
 * `POST /control/{a|b}/mode?value=normal|error|no-response|delay&delaySeconds=N`
 */
@RestController
class MockSupplierController {

    private val behaviors = ConcurrentHashMap<String, Behavior>()

    @PostMapping("/control/{supplier}/mode")
    fun changeMode(
        @PathVariable("supplier") supplier: String,
        @RequestParam("value") value: String,
        @RequestParam("delaySeconds", defaultValue = "0") delaySeconds: Long,
    ): String {
        val behavior = Behavior(Mode.of(value), Duration.ofSeconds(delaySeconds))
        behaviors[supplier] = behavior
        return "$supplier: $behavior"
    }

    @GetMapping("/a/v1/hotels", produces = [APPLICATION_JSON_VALUE])
    fun hotelsA(): String = SupplierFixtures.A_HOTELS

    @GetMapping("/b/api/properties", produces = [APPLICATION_JSON_VALUE])
    fun propertiesB(): String = SupplierFixtures.B_PROPERTIES

    @GetMapping("/a/v1/availability", produces = [APPLICATION_JSON_VALUE])
    fun availabilityA(): ResponseEntity<String> =
        respond(
            supplier = "a",
            success = SupplierFixtures.A_AVAILABILITY,
            // A 는 HTTP 상태 코드로 실패를 알린다
            failure = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SupplierFixtures.A_ERROR),
        )

    @GetMapping("/b/api/search", produces = [APPLICATION_JSON_VALUE])
    fun searchB(): ResponseEntity<String> =
        respond(
            supplier = "b",
            success = SupplierFixtures.B_SEARCH,
            // B 는 장애여도 HTTP 200 이고 본문 resultCode 로만 실패를 알린다
            failure = ResponseEntity.ok(SupplierFixtures.B_ERROR),
        )

    private fun respond(supplier: String, success: String, failure: ResponseEntity<String>): ResponseEntity<String> {
        val behavior = behaviors[supplier] ?: Behavior.NORMAL
        return when (behavior.mode) {
            Mode.NORMAL -> ResponseEntity.ok(success)
            Mode.ERROR -> failure
            Mode.NO_RESPONSE -> {
                Thread.sleep(NO_RESPONSE_HOLD)
                ResponseEntity.ok("{}")
            }
            Mode.DELAY -> {
                Thread.sleep(behavior.delay)
                ResponseEntity.ok(success)
            }
        }
    }

    data class Behavior(val mode: Mode, val delay: Duration) {
        companion object {
            val NORMAL = Behavior(Mode.NORMAL, Duration.ZERO)
        }
    }

    enum class Mode(private val param: String) {
        NORMAL("normal"),
        ERROR("error"),
        NO_RESPONSE("no-response"),
        DELAY("delay");

        companion object {
            fun of(param: String): Mode =
                entries.firstOrNull { it.param == param }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown mode: $param")
        }
    }

    companion object {
        /** 연결은 받되 응답을 주지 않는 시간. 앱의 타임아웃보다 충분히 길면 된다 */
        private val NO_RESPONSE_HOLD: Duration = Duration.ofMinutes(10)
    }
}
