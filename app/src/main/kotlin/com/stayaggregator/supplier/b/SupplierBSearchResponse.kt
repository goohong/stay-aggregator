package com.stayaggregator.supplier.b

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

/**
 * 공급사 B 의 재고·요금 응답. 스펙에 있는 필드를 모두 받는다 (ADR-0030).
 *
 * 껍데기는 [SupplierBPropertiesResponse] 와 같다. 장애여도 HTTP 200 이고 `resultCode` 로만 실패를 알린다.
 * 항목 하나가 숙소 하나의 객실 타입 하나다. 요금은 기간 총액 하나이고 세금액은 없이 포함 여부만 온다. 재고는 요금과 분리된 목록이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SupplierBSearchResponse(
    /** 스펙상 반드시 온다. `0000` 이 성공 */
    val resultCode: String?,
    val resultMessage: String?,
    /** 성공일 때만 값이 있고 실패하면 null 이다 */
    val data: Data?,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Data(
        val items: List<Item>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Item(
        val propertyId: String?,
        /** 응답에는 매핑 저장본의 이름을 쓴다 (ADR-0012). 이것은 목록과 비교하는 데만 쓴다 (ADR-0062) */
        val propertyName: String?,
        /** 이름과 달리 객실 타입이다 */
        val roomId: String?,
        /** 위와 같다 */
        val roomName: String?,
        /** 위와 같다 */
        val maxOccupancy: Int?,
        val breakfastIncluded: Boolean?,
        /** ISO 4217 코드 */
        val currency: String?,
        /** 기간 전체 총액. 통화의 최소 단위 정수 */
        val totalPrice: Long?,
        /** 총액에 세금이 들어 있는가. 세금액 자체는 오지 않는다 */
        val taxIncluded: Boolean?,
        /** 체크인일부터 체크아웃 전날까지 하루 하나 */
        val inventory: List<Inventory>?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Inventory(
        val date: LocalDate?,
        val remainingRooms: Int?,
    )
}
