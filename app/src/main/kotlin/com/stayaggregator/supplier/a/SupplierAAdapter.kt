package com.stayaggregator.supplier.a

import com.stayaggregator.supplier.AvailabilityRequest
import com.stayaggregator.supplier.FetchedAvailability
import com.stayaggregator.supplier.FetchedAvailabilityItem
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedDailyInventory
import com.stayaggregator.supplier.FetchedDailyRate
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedPricing
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierAdapter
import com.stayaggregator.supplier.SupplierHttp
import com.stayaggregator.supplier.apiKeyHeader
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * 공급사 A. 실패를 HTTP 상태 코드로 알린다.
 *
 * 상태 코드가 실패이거나 본문을 읽을 수 없으면 공급사 실패 예외가 되고, 그것이 ADR-0027 의 첫 질문에 대한 답이다.
 * 항목의 값이 유효한지는 여기서 보지 않는다 (ADR-0031).
 *
 * 요금은 날짜별 1박 금액과 세금액으로 온다. 더하지 않고 그대로 넘긴다. 더하려면 요청한 날짜가 다 왔는지 먼저 봐야 하고
 * 그것은 공급사 공통 검증이다 (ADR-0027 의 요금 표).
 */
@Component
class SupplierAAdapter(properties: StayProperties) : SupplierAdapter {

    override val supplierId = SUPPLIER_ID

    private val config = properties.of(SUPPLIER_ID)

    private val http = SupplierHttp(SUPPLIER_ID, config, apiKeyHeader(API_KEY_HEADER, config.apiKey))

    override fun fetchCatalog(): Mono<FetchedCatalog> =
        http.getCatalog("/a/v1/hotels", SupplierAHotelsResponse::class.java)
            .map { response -> response.toFetchedCatalog() }

    override fun fetchAvailability(request: AvailabilityRequest): Mono<FetchedAvailability> =
        http.getAvailability(SupplierAAvailabilityResponse::class.java) { builder ->
            builder.path("/a/v1/availability")
                .queryParam("hotelCodes", request.hotelCodes.joinToString(","))
                .queryParam("checkIn", request.period.checkIn)
                .queryParam("checkOut", request.period.checkOut)
                .queryParam("adults", request.guests.adults)
                .queryParam("children", request.guests.children)
        }
            .map { response -> response.toFetchedAvailability() }

    private fun SupplierAHotelsResponse.toFetchedCatalog() =
        FetchedCatalog(
            // 없는 것을 빈 목록으로 바꾸지 않는다. 검증은 정규화가 한다 (ADR-0041)
            hotels = items?.map { hotel ->
                FetchedHotel(
                    code = hotel.hotelCode,
                    name = hotel.hotelName,
                    roomTypes = hotel.roomTypes?.map { roomType ->
                        FetchedRoomType(
                            code = roomType.roomTypeCode,
                            name = roomType.roomTypeName,
                            maxOccupancy = roomType.maxOccupancy,
                        )
                    },
                )
            },
        )

    private fun SupplierAAvailabilityResponse.toFetchedAvailability() =
        FetchedAvailability(
            items = items?.map { item ->
                FetchedAvailabilityItem(
                    hotelCode = item.hotelCode,
                    roomTypeCode = item.roomTypeCode,
                    hotelName = item.hotelName,
                    roomTypeName = item.roomTypeName,
                    breakfastIncluded = item.breakfastIncluded,
                    currency = item.currency,
                    // 요금과 재고가 한 목록에 섞여 오므로 둘로 분리해 넘긴다
                    pricing = FetchedPricing.Daily(
                        rates = item.dailyRates?.map { day ->
                            FetchedDailyRate(date = day.date, nightlyRate = day.nightlyRate, taxAmount = day.taxAmount)
                        },
                    ),
                    dailyInventory = item.dailyRates?.map { day ->
                        FetchedDailyInventory(date = day.date, remainingRooms = day.remainingRooms)
                    },
                )
            },
        )

    companion object {
        const val SUPPLIER_ID = "a"
        private const val API_KEY_HEADER = "X-Api-Key"
    }
}
