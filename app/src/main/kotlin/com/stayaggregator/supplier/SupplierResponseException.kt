package com.stayaggregator.supplier

/**
 * 공급사 응답을 스펙대로 읽지 못했을 때 내는 예외(공급사 실패 예외). ADR-0027 의 첫 질문에 "아니요"인 경우다.
 *
 * 공급사마다 실패를 알리는 방법이 다르므로(HTTP 상태 코드, 본문의 결과 코드) 그 차이는 어댑터가 흡수하고,
 * 여기서부터는 한 가지 실패로 다룬다. 동기화는 이 공급사만 건너뛰고 다른 공급사는 그대로 간다 (ADR-0019).
 *
 * 만드는 곳은 넷이다. 공급사가 본문으로 알린 실패는 어댑터가 응답을 바꾸는 중에, 그 밖의 실패는 [asSupplierFailure] 가,
 * 응답이 비어 있는 경우는 동기화 서비스가, 목록을 담는 필드가 없는 경우는 정규화가 만든다 (ADR-0041).
 *
 * `RuntimeException` 을 상속한다. 이 예외가 지나는 경로는 시그니처에 예외를 적을 수 없는 곳이다.
 *
 * @property transient **재시도 가능한 실패인가** (ADR-0051).
 *   실패의 성질만 말하고, 실제로 재시도할지는 호출하는 쪽이 정한다. 검색은 재시도하고 목록 동기화는 재시도하지 않는다.
 *   **기본값은 아니요다.** 모르는 실패를 재시도하지 않는 쪽이 안전하다.
 */
class SupplierResponseException(
    message: String,
    cause: Throwable? = null,
    val transient: Boolean = false,
    /** 타임아웃으로 난 실패인가. 지표에서 타임아웃 비율을 따로 세려고 둔다 (ADR-0060) */
    val timedOut: Boolean = false,
    /**
     * 공급사가 요청 한도 초과(HTTP 429, B 의 `E429`)를 알린 실패인가. 재시도 가능한 실패이지만 다른 기준으로 기다린다 (ADR-0068).
     * [transient] 와 같은 곳에서 정한다. HTTP 상태는 [asSupplierFailure], 본문 결과 코드는 어댑터다
     */
    val throttled: Boolean = false,
) : RuntimeException(message, cause)
