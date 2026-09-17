package com.stayaggregator.search

import com.stayaggregator.domain.Money
import com.stayaggregator.domain.Rate
import com.stayaggregator.domain.RateConditions
import com.stayaggregator.domain.AvailableRoomType
import com.stayaggregator.domain.GuestCount
import com.stayaggregator.domain.StayPeriod
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.util.UUID

/**
 * 검색 API 의 계약을 본다. 응답 JSON 의 구조, 부분 실패의 표시, 잘못된 입력의 400.
 *
 * 검색 흐름 자체는 [SearchServiceIntegrationTest] 가 보고, 여기서는 서비스를 가짜로 두고 HTTP 계층만 본다.
 */
@WebMvcTest(SearchController::class)
class SearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var searchService: SearchService

    private val threeNights = StayPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8))
    private val oneNight = StayPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6))
    private val twoAdults = GuestCount(2, 0)

    private val hotelId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val roomTypeId = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun `응답에 요구된 최소 정보와 공급사별 상태가 실린다`() {
        val room = AvailableRoomType(hotelId, "강변 호텔", roomTypeId, "디럭스", 2, availableRooms = 1, rate = Rate(Money(396_000, "KRW"), RateConditions(false)))
        `when`(searchService.search(threeNights, twoAdults)).thenReturn(
            SearchResult(
                listOf(
                    SupplierResult.succeeded("a", listOf(room), outOfSpecCount = 1, failedChunks = 0),
                    SupplierResult.failed("b", "검색 전체 타임아웃 PT2S 안에 끝나지 않았다"),
                ),
            ),
        )

        mockMvc.get("/api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-08&adults=2&children=0")
            .andExpect {
                status { isOk() }
                jsonPath("$.checkIn") { value("2026-10-05") }
                jsonPath("$.nights") { value(3) }
                jsonPath("$.suppliers[0].supplier") { value("a") }
                jsonPath("$.suppliers[0].status") { value("SUCCEEDED") }
                jsonPath("$.suppliers[0].roomTypeCount") { value(1) }
                jsonPath("$.suppliers[0].outOfSpecCount") { value(1) }
                jsonPath("$.suppliers[1].supplier") { value("b") }
                jsonPath("$.suppliers[1].status") { value("FAILED") }
                jsonPath("$.suppliers[1].failureReason") { value("검색 전체 타임아웃 PT2S 안에 끝나지 않았다") }
                jsonPath("$.roomTypes[0].hotelId") { value(hotelId.toString()) }
                jsonPath("$.roomTypes[0].hotelName") { value("강변 호텔") }
                jsonPath("$.roomTypes[0].roomTypeId") { value(roomTypeId.toString()) }
                jsonPath("$.roomTypes[0].roomTypeName") { value("디럭스") }
                jsonPath("$.roomTypes[0].maxOccupancy") { value(2) }
                jsonPath("$.roomTypes[0].supplier") { value("a") }
                jsonPath("$.roomTypes[0].availableRooms") { value(1) }
                jsonPath("$.roomTypes[0].rate.totalAmount") { value(396_000) }
                jsonPath("$.roomTypes[0].rate.currency") { value("KRW") }
                jsonPath("$.roomTypes[0].rate.breakfastIncluded") { value(false) }
            }
    }

    @Test
    fun `예약 불가 상품도 예약 가능 객실 수 0 으로 응답에 나간다`() {
        val soldOut = AvailableRoomType(hotelId, "한옥", roomTypeId, "온돌", 2, availableRooms = 0, rate = Rate(Money(200_000, "KRW"), RateConditions(false)))
        `when`(searchService.search(oneNight, twoAdults))
            .thenReturn(SearchResult(listOf(SupplierResult.succeeded("a", listOf(soldOut), 0, 0))))

        mockMvc.get("/api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-06&adults=2&children=0")
            .andExpect {
                status { isOk() }
                jsonPath("$.roomTypes[0].availableRooms") { value(0) }
            }
    }

    @Test
    fun `서비스에 넘기는 값은 요청 파라미터로 만든 값 객체다`() {
        `when`(searchService.search(threeNights, GuestCount(0, 2)))
            .thenReturn(SearchResult(emptyList()))

        mockMvc.get("/api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-08&adults=0&children=2")
            .andExpect {
                status { isOk() }
                jsonPath("$.adults") { value(0) }
                jsonPath("$.children") { value(2) }
            }
    }

    @Test
    fun `체크아웃이 체크인보다 뒤가 아니면 400 이고 사유는 값 객체의 문장이다`() {
        mockMvc.get("/api/v1/stays/search?checkIn=2026-10-08&checkOut=2026-10-05&adults=2&children=0")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("체크아웃일이 체크인일보다 뒤가 아니다") }
            }
    }

    @Test
    fun `인원이 0명이면 400 이다`() {
        mockMvc.get("/api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-08&adults=0&children=0")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("인원이 0명이다") }
            }
    }

    @Test
    fun `날짜 형식이 틀리면 400 이다`() {
        mockMvc.get("/api/v1/stays/search?checkIn=20261005&checkOut=2026-10-08&adults=2&children=0")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `파라미터가 빠지면 400 이다`() {
        mockMvc.get("/api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-08&adults=2")
            .andExpect { status { isBadRequest() } }
    }
}
