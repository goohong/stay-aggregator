package com.stayaggregator.domain

/**
 * 값 객체가 생성자에서 값을 거부할 때 던지는 예외의 뿌리 (ADR-0041, ADR-0067).
 *
 * [field] 는 그 객체의 어느 값이 틀렸는지다. 받는 쪽(정규화)은 사유 문장이 아니라 이 필드로 문제 값을 정한다.
 * 정규화는 **이 타입만** 항목 거부로 받는다. 그 밖의 예외는 우리 코드의 결함이라 삼키지 않고 올린다 (ADR-0073).
 *
 * 스택 트레이스를 채우지 않는다. 검색마다 여러 항목이 거부될 수 있고, 어디서 던졌는지는 필드가 말한다.
 * `IllegalArgumentException` 을 상속해, 사유 문장만 쓰던 곳(요청 입력의 400 응답)은 그대로 동작한다.
 */
open class Rejected(open val field: Enum<*>, message: String) : IllegalArgumentException(message) {
    /** `IllegalArgumentException` 은 스택을 끄는 생성자가 없어 채우는 메서드를 비워 둔다 */
    override fun fillInStackTrace(): Throwable = this
}
