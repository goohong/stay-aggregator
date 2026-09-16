package com.stayaggregator.catalog

/**
 * 공급사 응답을 스펙대로 읽지 못했다는 신호. ADR-0027 의 첫 질문에 "아니요"인 경우다.
 *
 * 공급사마다 실패를 알리는 방법이 다르므로(HTTP 상태 코드, 본문의 결과 코드) 그 차이는 어댑터가 흡수하고,
 * 여기서부터는 한 가지 실패로 다룬다. 동기화는 이 공급사만 건너뛰고 다른 공급사는 그대로 간다 (ADR-0019).
 *
 * `RuntimeException` 을 상속한다. Spring 의 기본 설정에서 트랜잭션은 확인되지 않는 예외에만 되돌려지기 때문이다.
 */
class SupplierResponseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
