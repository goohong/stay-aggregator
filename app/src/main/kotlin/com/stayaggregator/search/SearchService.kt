package com.stayaggregator.search

import com.stayaggregator.mapping.MappedHotel
import com.stayaggregator.mapping.MappingRepository
import com.stayaggregator.quarantine.QuarantineEntry
import com.stayaggregator.quarantine.QuarantineRecorder
import com.stayaggregator.supplier.AvailabilityAdapter
import com.stayaggregator.supplier.AvailabilityRequest
import com.stayaggregator.supplier.GuestCount
import com.stayaggregator.supplier.StayPeriod
import com.stayaggregator.supplier.StayProperties
import com.stayaggregator.supplier.SupplierResponseException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.util.concurrent.TimeoutException

/**
 * 검색 한 건. 공급사마다 매핑을 읽고, 묶음으로 나눠 재고·요금을 물은 뒤, 판정해서 합친다.
 *
 * 공급사들은 동시에 부르고, 공급사 하나가 실패해도 나머지 결과로 응답한다 (ADR-0046).
 * 한 공급사 안에서는 묶음을 [StayProperties.Search.concurrencyPerSupplier] 개씩 동시에 부르고,
 * 묶음 일부가 실패하면 성공한 묶음은 내보내고 실패한 수를 센다 (ADR-0045, ADR-0050).
 * 묶음 호출이 **일시적인 실패**로 끝나면 정한 횟수만큼 다시 부른다 (ADR-0051). 목록 동기화는 다시 부르지 않는다 (ADR-0019).
 * 재시도 바깥에 공급사마다 서킷을 둔다. 재시도까지 거친 묶음의 최종 결과를 세고, 열려 있으면 그 공급사를 부르지 않는다 (ADR-0056, ADR-0057).
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
    private val circuitBreakers: SupplierCircuitBreakers,
    private val quarantine: QuarantineRecorder,
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

    /**
     * 묶음 하나. 실패해도 오류를 올리지 않고 실패했다는 결과로 바꾼다. 다른 묶음이 이어져야 하기 때문이다 (ADR-0050).
     *
     * 다시 부르는 것은 **공급사 호출까지만**이다. 응답을 우리 형태로 바꾸다 난 오류를 다시 불러도 같은 결과이고,
     * 그것은 애초에 일시적인 실패로 표시되지도 않는다 (ADR-0051).
     */
    private fun fetchChunk(adapter: AvailabilityAdapter, request: AvailabilityRequest, mapped: List<MappedHotel>): Mono<ChunkOutcome> =
        adapter.fetchAvailability(request)
            .retryWhen(retryTransient(adapter.supplierId, request))
            // 재시도 바깥이라 순간적인 실패는 재시도가 먼저 흡수하고, 다 실패한 묶음만 센다 (ADR-0056).
            // 검색 시간 한계로 취소될 때 받은 허가를 돌려주는 일도 이 연산자가 한다 (ADR-0057)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreakers.of(adapter.supplierId)))
            .map<ChunkOutcome> { fetched -> ChunkOutcome.Succeeded(normalizer.normalize(fetched, mapped, request.period, request.hotelCodes)) }
            .onErrorResume { e ->
                logChunkFailure(adapter.supplierId, request, e)
                Mono.just(ChunkOutcome.Failed(reason = chunkFailureReason(adapter.supplierId, e)))
            }

    private fun chunkFailureReason(supplierId: String, e: Throwable): String =
        when (e) {
            is CallNotPermittedException -> "공급사 $supplierId 의 서킷이 열려 있어 부르지 않았다"
            else -> e.message ?: e.javaClass.simpleName
        }

    /**
     * 일시적인 실패만 다시 부른다 (ADR-0051).
     *
     * 지수 백오프에 무작위를 섞는 것은 Reactor 가 기본으로 한다. 최대 대기는 걸지 않으면 사실상 무한이라 반드시 건다.
     * 다 써도 실패하면 **원래 실패를 그대로** 올린다. 감싸면 consumer 가 보는 신호가 달라져 ADR-0027 이 정한 "한 가지 실패"가 깨진다.
     */
    private fun retryTransient(supplierId: String, request: AvailabilityRequest): Retry =
        Retry.backoff(search.maxRetries.toLong(), search.retryMinBackoff)
            .maxBackoff(search.retryMaxBackoff)
            .filter { it is SupplierResponseException && it.transient }
            .doBeforeRetry { signal ->
                log.info(
                    "검색 묶음 재시도 supplier={} 숙소={}개 {}번째 이유={}",
                    supplierId,
                    request.hotelCodes.size,
                    signal.totalRetries() + 1,
                    signal.failure().message,
                )
            }
            .onRetryExhaustedThrow { _, signal -> signal.failure() }

    private fun combine(supplierId: String, outcomes: List<ChunkOutcome>, chunkCount: Int): SupplierResult {
        val succeeded = outcomes.filterIsInstance<ChunkOutcome.Succeeded>()
        val failed = outcomes.filterIsInstance<ChunkOutcome.Failed>()
        // 하나도 성공하지 못했으면 이 공급사의 응답을 만들 수 없다. 그것이 실패다 (ADR-0050).
        // 왜 실패했는지를 응답에 싣는다 (ADR-0046). 묶음이 여럿이면 원인이 대개 같으므로 마지막 것 하나만 붙인다
        if (succeeded.isEmpty()) {
            val reason = if (chunkCount == 1) failed.last().reason else "모든 묶음($chunkCount 개)을 쓰지 못했다. 마지막 원인: ${failed.last().reason}"
            return SupplierResult.failed(supplierId, reason, failedChunks = failed.size)
        }
        val failedChunks = failed.size
        val normalized = succeeded.map { it.result }
        val excluded = normalized.flatMap { it.excluded }
        if (excluded.isNotEmpty()) {
            log.info("검색 제외 supplier={} {}", supplierId, excluded.joinToString { "${it.hotelCode}/${it.roomTypeCode} ${it.kind}: ${it.reason}" })
            // 응답을 기다리게 하지 않고 뒤에서 쓴다 (ADR-0055)
            quarantine.record(excluded.map { QuarantineEntry(supplierId, it.hotelCode, it.roomTypeCode, it.value, it.reason, it.source) })
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
        if (e is CallNotPermittedException) {
            // 서킷이 연 것은 상태 변경 때 한 번 남겼다. 묶음마다 경고를 찍지 않는다
            log.debug("검색 묶음 건너뜀 supplier={} 숙소={}개 서킷 열림", supplierId, request.hotelCodes.size)
        } else if (e is SupplierResponseException) {
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
