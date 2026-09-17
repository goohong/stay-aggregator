package com.stayaggregator.quarantine

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 제외한 항목을 격리 기록에 남긴다 (ADR-0055).
 *
 * **응답을 기다리게 하지 않는다.** 기록은 뒤에서 쓰고, 실패해도 검색과 동기화를 실패시키지 않는다.
 * 격리는 분석을 위한 부가 기록이지 응답의 일부가 아니다. 그 대신 앱이 내려가는 순간 쓰지 못한 기록은 사라질 수 있다.
 */
@Component
class QuarantineRecorder(
    private val repository: QuarantineRepository,
    private val jsonMapper: JsonMapper,
    private val properties: QuarantineProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 기록마다 가상 스레드 하나. 블로킹 JDBC 를 요청 스레드 밖에서 부르려는 것이다 */
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    fun record(entries: List<QuarantineEntry>) {
        if (entries.isEmpty()) return
        executor.submit {
            entries.forEach { entry ->
                try {
                    repository.record(entry, toJson(entry.source))
                } catch (e: Exception) {
                    // 기록이 빠질 뿐 응답에는 영향이 없다 (ADR-0055)
                    log.warn("격리 기록 실패 supplier={} {}/{} value={}", entry.supplierId, entry.hotelCode, entry.roomTypeCode, entry.value, e)
                }
            }
        }
    }

    /** 보관 기간보다 오래 보지 않은 기록을 지운다. 목록 동기화가 돌 때 부른다 (ADR-0055) */
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
        executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)
    }

    companion object {
        /** 내려갈 때 쓰던 기록을 기다리는 한계. 넘으면 버린다 */
        private val SHUTDOWN_WAIT: Duration = Duration.ofSeconds(2)
    }
}

/** 격리 기록 설정 */
@ConfigurationProperties("stay.quarantine")
data class QuarantineProperties(
    /** 마지막으로 본 지 이만큼 지난 기록은 지운다. 값은 임시값이다 (ADR-0055) */
    val retention: Duration,
)
