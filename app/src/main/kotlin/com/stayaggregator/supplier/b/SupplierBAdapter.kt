package com.stayaggregator.supplier.b

import com.stayaggregator.supplier.AvailabilityRequest
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierAdapter
import com.stayaggregator.supplier.SupplierResponseException
import com.stayaggregator.supplier.asSupplierFailure
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * 공급사 B. 장애 상황에서도 HTTP 200 을 주고 본문의 결과 코드로만 실패를 알린다.
 *
 * 그 차이를 여기서 흡수해 A 의 HTTP 실패와 같은 신호([SupplierResponseException])로 바꾼다.
 * 이것이 ADR-0027 의 첫 질문에 대한 답이고, 항목의 값이 쓸 만한지는 여기서 보지 않는다 (ADR-0031).
 *
 * 요금은 기간 총액 하나로 온다. 세금이 포함됐는지는 포함 여부 값이 말하고 세금액은 없다. 그대로 넘긴다.
 */
@Component
class SupplierBAdapter(properties: StayProperties) : SupplierAdapter {

    override val supplierId = SUPPLIER_ID

    private val config = properties.of(SUPPLIER_ID)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(config.baseUrl)
        .defaultHeader(API_KEY_HEADER, config.apiKey)
        .build()

    override fun fetchCatalog(): Mono<FetchedCatalog> =
        webClient.get()
            .uri("/b/api/properties")
            .retrieve()
            .bodyToMono(SupplierBPropertiesResponse::class.java)
            .timeout(config.timeout)
            .asSupplierFailure(SUPPLIER_ID)
            .map { response -> response.toFetchedCatalog() }

    override fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability> =
        webClient.get()
            .uri { builder ->
                builder.path("/b/api/search")
                    .queryParam("propertyIds", request.hotelCodes.joinToString(","))
                    .queryParam("checkIn", request.period.checkIn)
                    .queryParam("checkOut", request.period.checkOut)
                    .queryParam("adults", request.guests.adults)
                    .queryParam("children", request.guests.children)
                    .build()
            }
            .retrieve()
            .bodyToMono(SupplierBSearchResponse::class.java)
            .timeout(config.timeout)
            .asSupplierFailure(SUPPLIER_ID)
            .map { response -> response.toFetchedAvailability() }

    private fun SupplierBPropertiesResponse.toFetchedCatalog(): FetchedCatalog {
        failIfNotSuccess(resultCode, resultMessage)
        return FetchedCatalog(
            // 없는 것을 빈 목록으로 바꾸지 않는다. 판정은 정규화가 한다 (ADR-0041)
            hotels = data?.items?.map { property ->
                FetchedHotel(
                    code = property.propertyId,
                    name = property.propertyName,
                    roomTypes = property.rooms?.map { room ->
                        FetchedRoomType(
                            code = room.roomId,
                            name = room.roomName,
                            maxOccupancy = room.maxOccupancy,
                        )
                    },
                )
            },
        )
    }

    private fun SupplierBSearchResponse.toFetchedAvailability(): FetchedAvailability {
        failIfNotSuccess(resultCode, resultMessage)
        return FetchedAvailability(
            items = data?.items?.map { item ->
                FetchedAvailabilityItem(
                    hotelCode = item.propertyId,
                    roomTypeCode = item.roomId,
                    breakfastIncluded = item.breakfastIncluded,
                    currency = item.currency,
                    pricing = FetchedPricing.Total(totalPrice = item.totalPrice, taxIncluded = item.taxIncluded),
                    dailyInventory = item.inventory?.map { day ->
                        FetchedDailyInventory(date = day.date, remainingRooms = day.remainingRooms)
                    },
                )
            },
        )
    }

    /** 두 API 의 껍데기가 같아 실패 판정도 같다. HTTP 200 이어도 결과 코드가 성공이 아니면 공급사 실패다 */
    private fun failIfNotSuccess(resultCode: String?, resultMessage: String?) {
        if (resultCode != SUCCESS_CODE) {
            throw SupplierResponseException("공급사 B 가 실패를 알렸다: resultCode=$resultCode, resultMessage=$resultMessage")
        }
    }

    companion object {
        const val SUPPLIER_ID = "b"
        private const val SUCCESS_CODE = "0000"
        private const val API_KEY_HEADER = "X-Api-Key"
    }
}
