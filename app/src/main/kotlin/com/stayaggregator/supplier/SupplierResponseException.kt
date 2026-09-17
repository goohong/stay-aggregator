package com.stayaggregator.supplier

/**
 * 공급사 응답을 스펙대로 읽지 못했다는 신호. ADR-0027 의 첫 질문에 "아니요"인 경우다.
 *
 * 공급사마다 실패를 알리는 방법이 다르므로(HTTP 상태 코드, 본문의 결과 코드) 그 차이는 어댑터가 흡수하고,
 * 여기서부터는 한 가지 실패로 다룬다. 동기화는 이 공급사만 건너뛰고 다른 공급사는 그대로 간다 (ADR-0019).
 *
 * 만드는 곳은 넷이다. 공급사가 본문으로 알린 실패는 어댑터가 응답을 바꾸는 중에, 그 밖의 실패는 [asSupplierFailure] 가,
 * 응답이 비어 있는 경우는 동기화 서비스가, 목록을 담는 필드가 없는 경우는 정규화가 만든다 (ADR-0041).
 *
 * `RuntimeException` 을 상속한다. 이 예외가 지나는 자리는 시그니처에 예외를 적을 수 없는 곳이다.
 *
 * @property transient **다시 불러 볼 여지가 있는 실패인가** (ADR-0051).
 *   실패의 성질만 말하고, 실제로 다시 부를지는 부르는 쪽이 정한다. 검색은 다시 부르고 목록 동기화는 부르지 않는다.
 *   **기본값은 아니요다.** 모르는 실패를 다시 부르지 않는 쪽이 안전하다.
 */
class SupplierResponseException(
    message: String,
    cause: Throwable? = null,
    val transient: Boolean = false,
    /** 정한 시간 안에 오지 않은 실패인가. 지표에서 타임아웃 비율을 따로 세려고 둔다 (ADR-0060) */
    val timedOut: Boolean = false,
) : RuntimeException(message, cause)
