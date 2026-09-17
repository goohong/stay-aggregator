package com.stayaggregator.supplier

import reactor.core.publisher.Mono

/**
 * 공급사의 재고·요금 API 를 부르고, 공급사마다 다른 표현을 [FetchedAvailability] 로 바꾼다 (ADR-0031).
 *
 * [CatalogAdapter] 와 같은 경계 규칙이다. 여기서 하는 판정은 ADR-0027 첫 질문 중 공급사마다 다른 부분
 * (HTTP 상태, 본문의 결과 코드, 본문을 읽을 수 있는가) 뿐이고, 항목의 값이 쓸 만한지는 보지 않는다.
 *
 * 요청 하나는 숙소 코드 50개 이하다. 나누는 일은 부르는 쪽이 한다 (ADR-0045).
 */
interface AvailabilityAdapter {

    /** 공급사를 가리키는 값. 응답에는 싣지 않으므로 consumer 는 어댑터와 응답을 함께 든다 (ADR-0049) */
    val supplierId: String

    fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability>
}
