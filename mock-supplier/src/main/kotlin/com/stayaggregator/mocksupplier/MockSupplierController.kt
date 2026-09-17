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
 * `POST /control/{a|b}/mode?value=normal|error|no-response|delay&delaySeconds=N`
 *
 * 모드는 그 공급사의 두 API(숙소 목록, 재고·요금)에 함께 적용된다.
 * 목록 API 에도 모드가 필요한 이유는 목록 응답을 읽지 못한 동기화의 동작(ADR-0037)과
 * 무응답 공급사가 있을 때의 기동(ADR-0038)을 확인해야 하기 때문이다.
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
    fun hotelsA(): ResponseEntity<String> =
        respond(
            supplier = "a",
            success = SupplierFixtures.A_HOTELS,
            failure = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SupplierFixtures.A_ERROR),
        )

    @GetMapping("/b/api/properties", produces = [APPLICATION_JSON_VALUE])
    fun propertiesB(): ResponseEntity<String> =
        respond(
            supplier = "b",
            success = SupplierFixtures.B_PROPERTIES,
            failure = ResponseEntity.ok(SupplierFixtures.B_ERROR),
        )

    /**
     * 숙소 코드가 50개를 넘으면 공급사 스펙대로 거절한다 (ADR-0045). 그 밖의 파라미터는 보지 않는다.
     * 스펙에 오류로 적힌 것까지만 흉내 낸다.
     */
    @GetMapping("/a/v1/availability", produces = [APPLICATION_JSON_VALUE])
    fun availabilityA(@RequestParam("hotelCodes", defaultValue = "") hotelCodes: String): ResponseEntity<String> {
        if (count(hotelCodes) > MAX_HOTEL_CODES) {
            return ResponseEntity.badRequest().body(SupplierFixtures.A_TOO_MANY_HOTEL_CODES)
        }
        return respond(
            supplier = "a",
            success = SupplierFixtures.A_AVAILABILITY,
            // A 는 HTTP 상태 코드로 실패를 알린다
            failure = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(SupplierFixtures.A_ERROR),
        )
    }

    @GetMapping("/b/api/search", produces = [APPLICATION_JSON_VALUE])
    fun searchB(@RequestParam("propertyIds", defaultValue = "") propertyIds: String): ResponseEntity<String> {
        if (count(propertyIds) > MAX_HOTEL_CODES) {
            // B 는 잘못된 요청도 HTTP 200 에 본문 코드로 알린다
            return ResponseEntity.ok(SupplierFixtures.B_BAD_REQUEST)
        }
        return respond(
            supplier = "b",
            success = SupplierFixtures.B_SEARCH,
            // B 는 장애여도 HTTP 200 이고 본문 resultCode 로만 실패를 알린다
            failure = ResponseEntity.ok(SupplierFixtures.B_ERROR),
        )
    }

    private fun count(commaSeparated: String): Int =
        if (commaSeparated.isBlank()) 0 else commaSeparated.split(',').size

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

        /** 두 공급사 모두 재고·요금 조회 한 번에 받는 숙소 코드 상한 */
        private const val MAX_HOTEL_CODES = 50
    }
}
