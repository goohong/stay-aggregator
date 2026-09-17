package com.stayaggregator.supplier

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 숙박 구간. 체크인일과 체크아웃일 한 쌍이다 (ADR-0043).
 *
 * 우리 검색 API 가 받는 개념이지만 공급사 호출의 인자이기도 해서 이 패키지에 둔다 (ADR-0047).
 * 바깥(우리 API, 공급사 호출)은 두 날짜를 쓰고 안쪽(판정)은 날짜 하나하나를 쓰므로, 그 변환을 여기서만 한다.
 *
 * 체크인일이 지난 날짜인지는 보지 않는다. 그 판정은 시간대 기준이 정해져야 한다 (Q28).
 */
data class StayPeriod(val checkIn: LocalDate, val checkOut: LocalDate) {
    init {
        // 같은 날도 안 된다. 0박은 숙박이 아니다
        require(checkOut.isAfter(checkIn)) { "체크아웃일이 체크인일보다 뒤가 아니다" }
    }

    val nights: Int
        get() = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    /**
     * 묵는 날짜들. 체크아웃일은 넣지 않는다.
     *
     * 3박이면 셋이다. 연박 판정(ADR-0023)과 빠진 날짜 판정(ADR-0027)이 이 목록과 응답을 대조한다.
     */
    fun nightDates(): List<LocalDate> = List(nights) { offset -> checkIn.plusDays(offset.toLong()) }
}
