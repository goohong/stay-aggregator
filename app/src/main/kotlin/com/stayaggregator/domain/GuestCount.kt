package com.stayaggregator.domain

/**
 * 검색 인원. 성인과 아동의 **수**다. 나이는 받지 않는다 (ADR-0048).
 *
 * 우리 검색 API 가 받는 개념이지만 공급사 호출의 인자이기도 해서 이 패키지에 둔다 (ADR-0047).
 *
 * 성인 최소 인원을 두지 않는다. 스펙도 한국 법도 아동만의 숙박을 막지 않고,
 * 인원이 객실에 맞는지는 공급사가 최대 수용 인원으로 걸러서 준다 (ADR-0048).
 * 여기서 막는 것은 값으로 성립하지 않는 것뿐이다.
 */
data class GuestCount(val adults: Int, val children: Int) {
    init {
        require(adults >= 0) { "성인 인원이 음수다" }
        require(children >= 0) { "아동 인원이 음수다" }
        require(adults + children >= 1) { "인원이 0명이다" }
    }

    val total: Int
        get() = adults + children
}
