package com.stayaggregator.search

import com.stayaggregator.domain.GuestCount
import com.stayaggregator.domain.StayPeriod
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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

    @Operation(
        summary = "날짜와 인원으로 여러 공급사의 재고·요금을 한 번에 검색한다",
        description = "공급사를 동시에 호출해 표준 모델로 정규화한 결과를 돌려준다. " +
            "공급사 하나가 실패해도 나머지 결과로 응답하고, 실패한 사실은 `suppliers` 에 드러난다. " +
            "예약 불가(예약 가능 객실 수 0)도 빼지 않고 그대로 내보낸다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "검색 결과. 모든 공급사가 실패해도 200 이고 `suppliers` 로 드러난다"),
        ApiResponse(responseCode = "400", description = "요청 값이 조건을 어겼다. 사유는 값 객체가 거부하며 낸 문장이다"),
    )
    @GetMapping("/api/v1/stays/search")
    fun search(
        @Parameter(description = "체크인일 (YYYY-MM-DD)", example = "2026-10-05", required = true)
        @RequestParam("checkIn") checkIn: LocalDate,
        @Parameter(description = "체크아웃일 (YYYY-MM-DD). 숙박에 포함하지 않는다", example = "2026-10-08", required = true)
        @RequestParam("checkOut") checkOut: LocalDate,
        @Parameter(description = "성인 수. 0 이상", example = "2", required = true)
        @RequestParam("adults") adults: Int,
        @Parameter(description = "아동 수. 0 이상이고 성인과 합이 1 이상이어야 한다", example = "0", required = true)
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
