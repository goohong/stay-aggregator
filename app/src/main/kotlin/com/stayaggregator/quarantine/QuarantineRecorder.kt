package com.stayaggregator.quarantine

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 제외한 항목을 격리 기록에 남긴다 (ADR-0055).
 *
 * **응답을 기다리게 하지 않는다.** 기록은 비동기로 쓰고, 실패해도 검색과 동기화를 실패시키지 않는다.
 * 격리는 분석을 위한 부가 기록이지 응답의 일부가 아니다. 그 대신 앱이 내려가는 순간 쓰지 못한 기록은 사라질 수 있다.
 *
 * **쓰는 스레드는 하나이고 큐에는 상한이 있다** (ADR-0073). 전에는 기록마다 가상 스레드를 띄워 상한이 없었고,
 * 공급사 하나가 응답 형식을 통째로 바꾸면 검색마다 수십 건의 upsert 가 DB 커넥션 풀(기본 10)을 놓고 매핑 읽기와 경쟁했다.
 * 스레드 하나면 격리 쓰기가 점유하는 커넥션이 최대 하나다. 큐가 차면 그 묶음은 버리고 경고와 지표로 남긴다.
 * 버려도 되는 이유는 같은 문제가 다음 검색에서 또 오기 때문이다. 격리 기록은 같은 문제를 한 행으로 그룹화한다 (ADR-0055).
 */
@Component
class QuarantineRecorder(
    private val repository: QuarantineRepository,
    private val jsonMapper: JsonMapper,
    private val properties: QuarantineProperties,
    /** 있으면 버린 묶음 수를 지표로 내보낸다 (ADR-0060). 테스트에서는 없어도 된다 */
    meterRegistry: io.micrometer.core.instrument.MeterRegistry? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val dropped = AtomicLong()

    /** 쓰는 가상 스레드 하나와 상한 있는 큐. 큐의 단위는 기록 한 묶음(검색이나 동기화 한 번이 낸 항목들)이다 */
    private val executor: ExecutorService = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(properties.queueCapacity),
        Thread.ofVirtual().name("quarantine-writer").factory(),
    ) { _, _ -> throw RejectedExecutionException() }

    init {
        meterRegistry?.gauge("stay.quarantine.dropped", dropped) { it.get().toDouble() }
    }

    fun record(entries: List<QuarantineEntry>) {
        if (entries.isEmpty()) return
        try {
            executor.execute {
                entries.forEach { entry ->
                    try {
                        repository.record(entry, toJson(entry.source))
                    } catch (e: Exception) {
                        // 기록이 빠질 뿐 응답에는 영향이 없다 (ADR-0055)
                        log.warn("격리 기록 실패 supplier={} {}/{} value={}", entry.supplierId, entry.hotelCode, entry.roomTypeCode, entry.value, e)
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            val total = dropped.incrementAndGet()
            log.warn("격리 기록 큐가 가득 차 묶음을 버림 항목={} 누적버림={} 큐상한={}", entries.size, total, properties.queueCapacity)
        }
    }

    /** 큐가 차서 버린 묶음 수. 지표가 없을 때 테스트가 본다 */
    fun droppedBatches(): Long = dropped.get()

    /** 보관 기간보다 오래 보지 않은 기록을 지운다. 목록 동기화가 돌 때 호출한다 (ADR-0055) */
    fun purgeExpired(): Int {
        val deleted = repository.deleteNotSeenSince(Instant.now().minus(properties.retention))
        if (deleted > 0) log.info("격리 기록 정리 지운행={} 보관기간={}", deleted, properties.retention)
        return deleted
    }

    private fun toJson(source: Any?): String? =
        source?.let {
            try {
                jsonMapper.writeValueAsString(it)
            } catch (e: Exception) {
                // 원본을 못 남겨도 사유와 횟수는 남긴다
                log.warn("격리 기록 원본 변환 실패 type={}", it.javaClass.simpleName, e)
                null
            }
        }

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
        if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
            val left = executor.shutdownNow().size
            log.warn("격리 기록을 다 쓰기 전에 종료함 남은묶음={} 기다린시간={}", left, SHUTDOWN_WAIT)
        }
    }

    companion object {
        /** 종료할 때 쓰던 기록을 기다리는 타임아웃. 넘으면 버린다 */
        private val SHUTDOWN_WAIT: Duration = Duration.ofSeconds(2)
    }
}

/** 격리 기록 설정 */
@ConfigurationProperties("stay.quarantine")
data class QuarantineProperties(
    /** 마지막으로 본 지 이만큼 지난 기록은 지운다 (ADR-0055). 값의 근거는 ADR-0068 에 있다 */
    val retention: Duration,
    /** 쓰기를 기다리는 묶음의 상한. 넘으면 버린다 (ADR-0073). 값의 근거는 `docs/tuning.md` 에 있다 */
    val queueCapacity: Int,
) {
    init {
        require(queueCapacity >= 1) { "격리 기록 큐 상한이 1 미만이다" }
    }
}
