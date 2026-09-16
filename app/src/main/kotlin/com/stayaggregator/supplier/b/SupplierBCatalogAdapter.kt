package com.stayaggregator.supplier.b

import com.stayaggregator.catalog.CatalogAdapter
import com.stayaggregator.catalog.FetchedCatalog
import com.stayaggregator.catalog.FetchedHotel
import com.stayaggregator.catalog.FetchedRoomType
import com.stayaggregator.catalog.SupplierResponseException
import com.stayaggregator.supplier.StayProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * 공급사 B 의 숙소 목록 조회. B 는 장애 상황에서도 HTTP 200 을 주고 본문의 결과 코드로만 실패를 알린다.
 *
 * 그 차이를 여기서 흡수해 A 의 HTTP 실패와 같은 신호([SupplierResponseException])로 바꾼다.
 * 이것이 ADR-0027 의 첫 질문에 대한 답이고, 항목의 값이 쓸 만한지는 여기서 보지 않는다 (ADR-0031).
 */
@Component
class SupplierBCatalogAdapter(properties: StayProperties) : CatalogAdapter {

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
            .map { response -> response.toFetchedCatalog() }

    private fun SupplierBPropertiesResponse.toFetchedCatalog(): FetchedCatalog {
        if (resultCode != SUCCESS_CODE) {
            throw SupplierResponseException("공급사 B 가 실패를 알렸다: resultCode=$resultCode, resultMessage=$resultMessage")
        }
        return FetchedCatalog(
            supplierId = SUPPLIER_ID,
            hotels = data?.items.orEmpty().map { property ->
                FetchedHotel(
                    code = property.propertyId,
                    name = property.propertyName,
                    roomTypes = property.rooms.orEmpty().map { room ->
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

    companion object {
        const val SUPPLIER_ID = "b"
        private const val SUCCESS_CODE = "0000"
        private const val API_KEY_HEADER = "X-Api-Key"
    }
}
