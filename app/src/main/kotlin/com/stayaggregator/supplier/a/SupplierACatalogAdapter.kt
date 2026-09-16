package com.stayaggregator.supplier.a

import com.stayaggregator.supplier.CatalogAdapter
import com.stayaggregator.supplier.FetchedCatalog
import com.stayaggregator.supplier.FetchedHotel
import com.stayaggregator.supplier.FetchedRoomType
import com.stayaggregator.supplier.StayProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * 공급사 A 의 숙소 목록 조회. A 는 실패를 HTTP 상태 코드로 알린다.
 *
 * 상태 코드가 실패이거나 본문을 읽을 수 없으면 오류 신호가 되고, 그것이 ADR-0027 의 첫 질문에 대한 답이다.
 * 항목의 값이 쓸 만한지는 여기서 보지 않는다 (ADR-0031).
 */
@Component
class SupplierACatalogAdapter(properties: StayProperties) : CatalogAdapter {

    override val supplierId = SUPPLIER_ID

    private val config = properties.of(SUPPLIER_ID)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(config.baseUrl)
        .defaultHeader(API_KEY_HEADER, config.apiKey)
        .build()

    override fun fetchCatalog(): Mono<FetchedCatalog> =
        webClient.get()
            .uri("/a/v1/hotels")
            .retrieve()
            .bodyToMono(SupplierAHotelsResponse::class.java)
            .timeout(config.timeout)
            .map { response -> response.toFetchedCatalog() }

    private fun SupplierAHotelsResponse.toFetchedCatalog() =
        FetchedCatalog(
            supplierId = SUPPLIER_ID,
            hotels = items.orEmpty().map { hotel ->
                FetchedHotel(
                    code = hotel.hotelCode,
                    name = hotel.hotelName,
                    roomTypes = hotel.roomTypes.orEmpty().map { roomType ->
                        FetchedRoomType(
                            code = roomType.roomTypeCode,
                            name = roomType.roomTypeName,
                            maxOccupancy = roomType.maxOccupancy,
                        )
                    },
                )
            },
        )

    companion object {
        const val SUPPLIER_ID = "a"
        private const val API_KEY_HEADER = "X-Api-Key"
    }
}
