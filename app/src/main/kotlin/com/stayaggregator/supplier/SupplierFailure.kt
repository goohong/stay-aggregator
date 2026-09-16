package com.stayaggregator.supplier

import reactor.core.publisher.Mono

/**
 * 공급사마다 다른 실패 표현을 한 가지 신호로 바꾼다 (ADR-0027 의 첫 질문, ADR-0031).
 *
 * HTTP 상태 코드로 오는 실패, 본문을 읽지 못한 것, 정한 시간 안에 오지 않은 것이 모두 여기로 들어온다.
 * 공급사가 본문의 결과 코드로 알린 실패는 어댑터가 이미 [SupplierResponseException] 으로 바꿔 두므로 그대로 둔다.
 *
 * 이것이 없으면 실패를 HTTP 로 알리는 공급사와 본문으로 알리는 공급사가 소비자에게 다른 예외로 보인다.
 */
fun <T : Any> Mono<T>.asSupplierFailure(supplierId: String): Mono<T> =
    onErrorMap({ it !is SupplierResponseException }) { cause ->
        SupplierResponseException("공급사 $supplierId 응답을 쓸 수 없다: ${cause.message}", cause)
    }
