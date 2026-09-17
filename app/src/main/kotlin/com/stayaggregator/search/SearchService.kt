package com.stayaggregator.search

import com.stayaggregator.mapping.MappedHotel
import com.stayaggregator.mapping.MappingRepository
import com.stayaggregator.supplier.AvailabilityAdapter
import com.stayaggregator.supplier.AvailabilityRequest
import com.stayaggregator.supplier.GuestCount
import com.stayaggregator.supplier.StayPeriod
import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierResponseException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException

/**
 * 검색 한 건. 공급사마다 매핑을 읽고, 묶음으로 나눠 재고·요금을 물은 뒤, 판정해서 합친다.
 *
 * 공급사들은 동시에 부르고, 공급사 하나가 실패해도 나머지 결과로 응답한다 (ADR-0046).
 * 한 공급사 안에서는 묶음을 [StayProperties.Search.concurrencyPerSupplier] 개씩 동시에 부르고,
 * 묶음 일부가 실패하면 성공한 묶음은 내보내고 실패한 수를 센다 (ADR-0045, ADR-0050).
 * 검색 전체 시간 한계는 공급사마다 건다. 넘긴 공급사만 실패가 되고 나머지는 그대로 나간다.
 *
 * 기다리는 자리는 여기다. 가상 스레드 위에서 `block` 한다 (ADR-0021).
 * 매핑 읽기도 그 스레드에서 블로킹으로 한다. 별도 스케줄러를 두지 않는 것이 ADR-0021 이 가상 스레드를 고른 이유다.
 * 공급사마다 읽기가 차례로 일어나지만 밀리초 단위이고, 공급사 HTTP 호출은 그 뒤에 각자 비동기로 나가 동시성을 잃지 않는다.
 * 어느 공급사인지는 어댑터만 알므로 어댑터와 그 결과를 여기서 함께 든다 (ADR-0049).
 */
@Service
class SearchService(
    private val adapters: List<AvailabilityAdapter>,
    private val repository: MappingRepository,
    private val normalizer: AvailabilityNormalizer,
    properties: StayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val search = properties.search

    fun search(period: StayPeriod, guests: GuestCount): SearchResult {
        val results = Flux.fromIterable(adapters)
            .flatMap { adapter -> searchSupplier(adapter, period, guests) }
            .collectList()
            .block()!!
        // 주입 순서에 기대지 않으므로 응답 순서는 여기서 정한다 (ADR-0031)
        return SearchResult(results.sortedBy { it.supplierId })
    }

    private fun searchSupplier(adapter: AvailabilityAdapter, period: StayPeriod, guests: GuestCount): Mono<SupplierResult> =
        Mono.fromCallable { repository.findActiveHotels(adapter.supplierId) }
            .flatMap { mapped -> fetchAll(adapter, mapped, period, guests) }
            .timeout(search.budget)
            .onErrorResume { e -> Mono.just(failed(adapter.supplierId, e)) }

    /** 매핑의 숙소 코드를 50개씩 나눠 부르고, 묶음마다 판정한 결과를 모은다 */
    private fun fetchAll(adapter: AvailabilityAdapter, mapped: List<MappedHotel>, period: StayPeriod, guests: GuestCount): Mono<SupplierResult> {
        if (mapped.isEmpty()) {
            return Mono.just(SupplierResult.succeeded(adapter.supplierId, emptyList(), outOfSpecCount = 0, failedChunks = 0))
        }
        val chunks = mapped.map { it.supplierHotelCode }.chunked(AvailabilityRequest.MAX_HOTEL_CODES)
        return Flux.fromIterable(chunks)
            .flatMap({ codes -> fetchChunk(adapter, AvailabilityRequest(codes, period, guests), mapped) }, search.concurrencyPerSupplier)
            .collectList()
            .map { outcomes -> combine(adapter.supplierId, outcomes, chunks.size) }
    }

    /** 묶음 하나. 실패해도 오류를 올리지 않고 실패했다는 결과로 바꾼다. 다른 묶음이 이어져야 하기 때문이다 (ADR-0050) */
    private fun fetchChunk(adapter: AvailabilityAdapter, request: AvailabilityRequest, mapped: List<MappedHotel>): Mono<ChunkOutcome> =
        adapter.fetchAvailability(request)
            .map<ChunkOutcome> { fetched -> ChunkOutcome.Succeeded(normalizer.normalize(fetched, mapped, request.period)) }
            .onErrorResume { e ->
                logChunkFailure(adapter.supplierId, request, e)
                Mono.just(ChunkOutcome.Failed(reason = e.message ?: e.javaClass.simpleName))
            }

    private fun combine(supplierId: String, outcomes: List<ChunkOutcome>, chunkCount: Int): SupplierResult {
        val succeeded = outcomes.filterIsInstance<ChunkOutcome.Succeeded>()
        val failed = outcomes.filterIsInstance<ChunkOutcome.Failed>()
        // 하나도 성공하지 못했으면 이 공급사의 응답을 만들 수 없다. 그것이 실패다 (ADR-0050).
        // 왜 실패했는지를 응답에 싣는다 (ADR-0046). 묶음이 여럿이면 원인이 대개 같으므로 마지막 것 하나만 붙인다
        if (succeeded.isEmpty()) {
            val reason = if (chunkCount == 1) failed.last().reason else "모든 묶음 호출($chunkCount 개)이 실패했다. 마지막 원인: ${failed.last().reason}"
            return SupplierResult.failed(supplierId, reason, failedChunks = failed.size)
        }
        val failedChunks = failed.size
        val normalized = succeeded.map { it.result }
        val excluded = normalized.flatMap { it.excluded }
        if (excluded.isNotEmpty()) {
            log.info("검색 제외 supplier={} {}", supplierId, excluded.joinToString { "${it.hotelCode}/${it.roomTypeCode} ${it.kind}: ${it.reason}" })
        }
        return SupplierResult.succeeded(
            supplierId,
            roomTypes = normalized.flatMap { it.available },
            outOfSpecCount = normalized.sumOf { it.outOfSpecCount },
            failedChunks = failedChunks,
        )
    }

    private fun failed(supplierId: String, e: Throwable): SupplierResult {
        val reason = when (e) {
            is TimeoutException -> "검색 시간 한계 ${search.budget} 안에 끝나지 않았다"
            else -> e.message ?: e.javaClass.simpleName
        }
        if (e is SupplierResponseException || e is TimeoutException) {
            log.warn("검색 공급사 실패 supplier={} 이유={}", supplierId, reason)
        } else {
            // 공급사가 알린 실패가 아니면 우리 쪽 결함일 수 있어 어디서 났는지까지 남긴다
            log.warn("검색 공급사 실패 supplier={} 이유={}", supplierId, reason, e)
        }
        return SupplierResult.failed(supplierId, reason)
    }

    private fun logChunkFailure(supplierId: String, request: AvailabilityRequest, e: Throwable) {
        if (e is SupplierResponseException) {
            log.warn("검색 묶음 실패 supplier={} 숙소={}개 이유={}", supplierId, request.hotelCodes.size, e.message)
        } else {
            log.warn("검색 묶음 실패 supplier={} 숙소={}개 이유={}", supplierId, request.hotelCodes.size, e.message, e)
        }
    }

    private sealed interface ChunkOutcome {
        data class Succeeded(val result: NormalizedAvailability) : ChunkOutcome
        data class Failed(val reason: String) : ChunkOutcome
    }
}
