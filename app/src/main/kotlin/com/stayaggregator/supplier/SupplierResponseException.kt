package com.stayaggregator.supplier

/**
 * 공급사 응답을 스펙대로 읽지 못했다는 신호. ADR-0027 의 첫 질문에 "아니요"인 경우다.
 *
 * 공급사마다 실패를 알리는 방법이 다르므로(HTTP 상태 코드, 본문의 결과 코드) 그 차이는 어댑터가 흡수하고,
 * 여기서부터는 한 가지 실패로 다룬다. 동기화는 이 공급사만 건너뛰고 다른 공급사는 그대로 간다 (ADR-0019).
 *
 * `RuntimeException` 을 상속한다. 이 예외는 어댑터의 `map` 안에서 던져져 `block()` 에서 그대로 다시 나오는데,
 * 그 자리는 시그니처에 예외를 적을 수 없는 곳이다.
 */
class SupplierResponseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
