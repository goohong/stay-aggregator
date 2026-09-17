package com.stayaggregator.catalog

import com.stayaggregator.mapping.MappingRepository
import com.stayaggregator.supplier.CatalogAdapter
import com.stayaggregator.supplier.SupplierResponseException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 공급사마다 숙소 목록을 받아 매핑에 반영한다 (ADR-0013).
 *
 * 공급사 하나가 실패해도 나머지는 그대로 간다 (ADR-0019). 실패한 공급사의 매핑은 이전 상태로 남는다.
 * 기다리는 자리는 여기다. 공급사 호출이 끝난 뒤에 저장이 시작되므로 트랜잭션이 HTTP 호출을 감싸지 않는다 (ADR-0038).
 *
 * 어느 공급사인지는 어댑터만 안다. 응답과 정규화 결과에는 없으므로 저장소에 넘길 때 어댑터 값을 쓴다 (ADR-0049).
 */
@Service
class CatalogSyncService(
    private val adapters: List<CatalogAdapter>,
    private val normalizer: CatalogNormalizer,
    private val repository: MappingRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun syncAll() {
        adapters.forEach { adapter -> sync(adapter) }
    }

    private fun sync(adapter: CatalogAdapter) {
        try {
            val fetched = adapter.fetchCatalog().block()
                ?: throw SupplierResponseException("공급사 ${adapter.supplierId} 응답이 비어 있다")
            val normalized = normalizer.normalize(fetched)
            val applied = repository.applyCatalog(adapter.supplierId, normalized.hotels)
            log.info(
                "목록 동기화 완료 supplier={} 숙소={} 객실타입={} 목록에없는숙소={} 제외={} {}",
                adapter.supplierId,
                applied.hotels,
                applied.roomTypes,
                applied.missingHotels,
                normalized.excluded.size,
                normalized.excluded.joinToString { "${it.hotelCode}/${it.roomTypeCode}: ${it.reason}" },
            )
        } catch (e: SupplierResponseException) {
            // 응답을 스펙대로 받지 못한 실패다. 공급사가 알렸든 오지 않았든 원인이 메시지에 있어 스택은 남기지 않는다 (ADR-0019).
            log.warn("목록 동기화 실패 supplier={} 이유={}", adapter.supplierId, e.message)
        } catch (e: Exception) {
            // 공급사가 알린 실패가 아니면 우리 쪽 결함일 수 있어 어디서 났는지까지 남긴다.
            log.warn("목록 동기화 실패 supplier={} 이유={}", adapter.supplierId, e.message, e)
        }
    }
}
