package com.stayaggregator.search

import com.stayaggregator.domain.GuestCount
import com.stayaggregator.domain.StayPeriod
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * `GET /api/v1/stays/search?checkIn=&checkOut=&adults=&children=`
 *
 * 검색 조건은 날짜와 인원뿐이다. 입력 검사는 값 객체의 생성자가 한다 (ADR-0041, ADR-0043, ADR-0048).
 * 거부되면 [SearchErrorHandler] 가 400 으로 바꾼다.
 *
 * 체크인일이 지난 날짜인지는 보지 않는다. 어느 시간대 기준으로 볼지가 정해지지 않았다 (Q28).
 *
 * 응답 타입이 `Mono` 가 아니다. 공급사 응답은 서비스 안에서 가상 스레드 위에서 기다린다 (ADR-0021).
 */
@RestController
class SearchController(private val searchService: SearchService) {

    @GetMapping("/api/v1/stays/search")
    fun search(
        @RequestParam("checkIn") checkIn: LocalDate,
        @RequestParam("checkOut") checkOut: LocalDate,
        @RequestParam("adults") adults: Int,
        @RequestParam("children") children: Int,
    ): SearchResponse {
        val period = StayPeriod(checkIn, checkOut)
        val guests = GuestCount(adults, children)
        val result = searchService.search(period, guests)
        return SearchResponse.from(
            result,
            checkIn = checkIn.toString(),
            checkOut = checkOut.toString(),
            nights = period.nights,
            adults = adults,
            children = children,
        )
    }
}
