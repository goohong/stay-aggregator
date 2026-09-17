package com.stayaggregator.supplier

import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

/**
 * 공급사마다 다른 실패 표현을 한 가지 예외([SupplierResponseException])로 바꾼다 (ADR-0027 의 첫 질문, ADR-0031).
 *
 * HTTP 상태 코드로 오는 실패, 본문을 읽지 못한 것, 연결하지 못한 것, 정한 시간 안에 오지 않은 것이 여기로 들어온다.
 * 공급사가 본문의 결과 코드로 알린 실패는 어댑터가 응답을 바꾸는 중에, 즉 이 함수 **아래에서** [SupplierResponseException] 으로 바꾼다.
 * `onErrorMap` 은 위에서 난 오류만 보므로 그것은 이 함수를 지나간다.
 *
 * "이미 우리 예외면 바꾸지 않는다" 는 조건은 두지 않는다. 위에서 그 예외가 올 경로가 없어 실행되지 않는 검사가 되고,
 * 조건이 없으면 자리를 뒤로 잘못 옮겼을 때 메시지가 겹쳐 보여 그 실수가 드러난다.
 *
 * 이것이 없으면 실패를 HTTP 로 알리는 공급사와 본문으로 알리는 공급사가 consumer 에게 다른 예외로 보인다.
 *
 * **응답을 우리 형태로 바꾸기 전에 붙인다.** 뒤에 붙이면 그 변환 코드에서 난 우리 쪽 오류까지 공급사 실패가 되어,
 * 동기화가 원인을 남기지 않고 한 줄만 찍는다.
 *
 * 원인 클래스 이름을 메시지에 넣는다. 예외에 따라 `message` 가 비어 있고, 로그에서 실패 종류를 가릴 방법이 이것뿐이다.
 *
 * 여기서 [SupplierResponseException.transient] 도 정한다. HTTP 상태와 연결·시간 초과는 공급사마다 다르지 않아 공통인 이 자리에서 본다.
 * 본문 결과 코드는 체계가 공급사마다 달라 어댑터가 본다 (ADR-0051).
 */
fun <T : Any> Mono<T>.asSupplierFailure(supplierId: String): Mono<T> =
    onErrorMap { cause ->
        SupplierResponseException(
            "공급사 $supplierId 응답을 쓸 수 없다: ${cause.javaClass.simpleName}: ${cause.message}",
            cause,
            transient = isTransient(cause),
            timedOut = cause is java.util.concurrent.TimeoutException || isNettyTimeout((cause as? WebClientRequestException)?.cause),
        )
    }

/**
 * 재시도 가능한(transient) 실패인지 본다 (ADR-0051 의 표).
 *
 * **타임아웃은 재시도 가능하지 않다고 본다.** 우리가 건 타임아웃이든 Netty 의 연결·읽기 타임아웃이든 같다. 공급사가 느리다는 신호이고, 느린 공급사를 재시도해도 느릴 가능성이 높다.
 * 본문을 읽지 못한 것도 재시도 가능하지 않다. 응답 구조의 문제라 재시도해도 같다. 둘 다 여기서 걸리지 않고 기본값(아니요)으로 떨어진다.
 */
private fun isTransient(cause: Throwable): Boolean =
    when (cause) {
        // 공급사가 상태 코드로 알린 실패. 5xx 는 "지금은 안 된다", 429 는 "기다렸다 재시도하라" 다
        is WebClientResponseException -> cause.statusCode.is5xxServerError || cause.statusCode.value() == TOO_MANY_REQUESTS
        // 요청 단계의 실패. 연결을 거절당한 것은 빠르게 실패하고 공급사 재시작 같은 순간적 상황일 수 있다.
        // 다만 Netty 가 낸 시간 초과(연결·읽기)는 "느리다"는 신호라 일시적이지 않다.
        // 우리가 거는 Mono.timeout 은 exchange 바깥이라 감싸이지 않은 TimeoutException 으로 와 아래 else 로 간다
        is WebClientRequestException -> !isNettyTimeout(cause.cause)
        else -> false
    }

private fun isNettyTimeout(cause: Throwable?): Boolean =
    cause is io.netty.channel.ConnectTimeoutException || cause is io.netty.handler.timeout.TimeoutException

private const val TOO_MANY_REQUESTS = 429
