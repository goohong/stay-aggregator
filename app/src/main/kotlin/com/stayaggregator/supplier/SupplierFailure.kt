package com.stayaggregator.supplier

import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

/**
 * 공급사마다 다른 실패 표현을 한 가지 예외([SupplierFailure])로 바꾼다 (ADR-0027 의 첫 질문, ADR-0031).
 *
 * HTTP 상태 코드로 오는 실패, 본문을 읽지 못한 것, 연결하지 못한 것, 타임아웃이 여기로 들어온다.
 * 공급사가 본문의 결과 코드로 알린 실패는 어댑터가 응답을 바꾸는 중에, 즉 이 함수 **아래에서** [SupplierFailure] 으로 바꾼다.
 * `onErrorMap` 은 위에서 난 오류만 보므로 그것은 이 함수를 지나간다.
 *
 * "이미 우리 예외면 바꾸지 않는다" 는 조건은 두지 않는다. 위에서 그 예외가 올 경로가 없어 실행되지 않는 검사가 되고,
 * 조건이 없으면 이 함수의 위치를 뒤로 잘못 옮겼을 때 메시지가 겹쳐 보여 그 실수가 드러난다.
 *
 * 이것이 없으면 실패를 HTTP 로 알리는 공급사와 본문으로 알리는 공급사가 consumer 에게 다른 예외로 보인다.
 *
 * **응답을 우리 형태로 바꾸기 전에 붙인다.** 뒤에 붙이면 그 변환 코드에서 난 내부 오류까지 공급사 실패가 되어,
 * 동기화가 원인을 남기지 않고 한 줄만 찍는다.
 *
 * 원인 클래스 이름을 메시지에 넣는다. 예외에 따라 `message` 가 비어 있고, 로그에서 실패 종류를 가릴 방법이 이것뿐이다.
 *
 * 실패의 종류도 여기서 정한다. HTTP 상태와 연결·시간 초과는 공급사마다 다르지 않아 공통 함수인 여기서 본다.
 * 본문 결과 코드는 체계가 공급사마다 달라 어댑터가 본다 (ADR-0051).
 */
/**
 * 공급사 응답을 스펙대로 받지 못했을 때 내는 예외(공급사 실패 예외). ADR-0027 의 첫 질문에 "아니요"인 경우다.
 *
 * 공급사마다 실패를 알리는 방법이 다르므로(HTTP 상태 코드, 본문의 결과 코드) 그 차이는 어댑터가 흡수하고,
 * consumer 는 이 루트 타입 하나만 잡는다. 동기화는 이 공급사만 건너뛰고 다른 공급사는 그대로 간다 (ADR-0019).
 *
 * **종류는 sealed 하위 타입이다** (ADR-0070). 재시도 가능 여부는 종류가 정하고, 지표·재시도·로그는 `when` 으로 가른다.
 * 종류가 늘면 `when` 이 컴파일되지 않아 해석하는 곳이 드러난다. 전에는 boolean 셋(`transient`·`timedOut`·`throttled`)이었고
 * 해석이 다섯 곳에 흩어져 있었다.
 *
 * 만드는 곳은 넷이다. 공급사가 본문으로 알린 실패는 어댑터가 응답을 바꾸는 중에, 그 밖의 실패는 [asSupplierFailure] 가,
 * 응답이 비어 있는 경우는 동기화 서비스가, 목록을 담는 필드가 없는 경우는 정규화가 만든다 (ADR-0041).
 *
 * `RuntimeException` 을 상속한다. 이 예외가 지나는 경로는 시그니처에 예외를 적을 수 없는 곳이다.
 */
sealed class SupplierFailure(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {

    /**
     * **재시도 가능한 실패인가** (ADR-0051). 실패의 성질만 말하고, 실제로 재시도할지는 호출하는 쪽이 정한다.
     * 검색은 재시도하고 목록 동기화는 재시도하지 않는다.
     */
    abstract val transient: Boolean

    /** 정한 시간 안에 오지 않았다. 우리가 건 타임아웃이든 Netty 의 연결·읽기 타임아웃이든 같다. 느리다는 신호라 재시도하지 않는다 */
    class Timeout(message: String, cause: Throwable? = null) : SupplierFailure(message, cause) {
        override val transient = false
    }

    /** 공급사가 요청 한도 초과를 알렸다 (HTTP 429, B 의 `E429`). 재시도 가능하지만 5xx 와 다른 기준으로 기다린다 (ADR-0068) */
    class Throttled(message: String, cause: Throwable? = null) : SupplierFailure(message, cause) {
        override val transient = true
    }

    /** 공급사가 지금은 처리할 수 없다고 알렸거나 연결을 받지 않았다 (5xx, B 의 `E5xx`, 연결 거절). 순간적일 수 있어 재시도 가능하다 */
    class Unavailable(message: String, cause: Throwable? = null) : SupplierFailure(message, cause) {
        override val transient = true
    }

    /** 공급사가 요청을 거절했다 (4xx, B 의 `E400`·`E401`). 같은 요청을 다시 보내면 같은 거절이라 재시도하지 않는다 */
    class Rejected(message: String, cause: Throwable? = null) : SupplierFailure(message, cause) {
        override val transient = false
    }

    /** 응답이 왔지만 스펙대로 읽지 못했다 (본문 해석 실패, 목록 필드 없음, 빈 응답). 구조의 문제라 재시도해도 같다 */
    class Unreadable(message: String, cause: Throwable? = null) : SupplierFailure(message, cause) {
        override val transient = false
    }
}

fun <T : Any> Mono<T>.asSupplierFailure(supplierId: String): Mono<T> =
    onErrorMap { cause -> classify(supplierId, cause) }

/**
 * HTTP 계층의 실패를 종류로 가른다 (ADR-0051 의 표, ADR-0068).
 *
 * - 우리가 건 `Mono.timeout` 은 exchange 바깥이라 감싸이지 않은 `TimeoutException` 으로 온다. Netty 의 연결·읽기 타임아웃은
 *   `WebClientRequestException` 안에 있다. 둘 다 [SupplierFailure.Timeout] 이다
 * - 상태 코드로 알린 실패: 429 는 [SupplierFailure.Throttled], 5xx 는 [SupplierFailure.Unavailable], 그 밖의 4xx 는 [SupplierFailure.Rejected]
 * - 요청 단계의 실패(연결 거절 등)는 공급사 재시작 같은 순간적 상황일 수 있어 [SupplierFailure.Unavailable]
 * - 나머지(본문을 읽지 못함 등)는 [SupplierFailure.Unreadable]. 모르는 실패를 재시도하지 않는 쪽이 안전하다
 */
private fun classify(supplierId: String, cause: Throwable): SupplierFailure {
    val message = "공급사 $supplierId 응답을 쓸 수 없다: ${cause.javaClass.simpleName}: ${cause.message}"
    return when {
        cause is java.util.concurrent.TimeoutException -> SupplierFailure.Timeout(message, cause)
        cause is WebClientRequestException && isNettyTimeout(cause.cause) -> SupplierFailure.Timeout(message, cause)
        cause is WebClientRequestException -> SupplierFailure.Unavailable(message, cause)
        cause is WebClientResponseException && cause.statusCode.value() == TOO_MANY_REQUESTS -> SupplierFailure.Throttled(message, cause)
        cause is WebClientResponseException && cause.statusCode.is5xxServerError -> SupplierFailure.Unavailable(message, cause)
        cause is WebClientResponseException -> SupplierFailure.Rejected(message, cause)
        else -> SupplierFailure.Unreadable(message, cause)
    }
}

private fun isNettyTimeout(cause: Throwable?): Boolean =
    cause is io.netty.channel.ConnectTimeoutException || cause is io.netty.handler.timeout.TimeoutException

private const val TOO_MANY_REQUESTS = 429
