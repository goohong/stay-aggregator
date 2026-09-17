package com.stayaggregator.quarantine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 격리 기록 쓰기가 상한 있는 큐를 쓰는지 (ADR-0073). 쓰기가 막혀 큐가 차면 묶음을 버리고 세며, 응답 쪽을 막지 않는다.
 */
class QuarantineRecorderTest {

    @Test
    fun `큐가 가득 차면 묶음을 버리고 버린 수를 센다`() {
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        // 첫 쓰기가 잠겨 있는 동안 뒤 묶음이 큐에 쌓인다. DB 는 쓰지 않는다
        val repository = object : QuarantineRepository(Mockito.mock(org.springframework.jdbc.core.simple.JdbcClient::class.java)) {
            override fun record(entry: QuarantineEntry, sourceJson: String?) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        val recorder = QuarantineRecorder(repository, JsonMapper.builder().build(), QuarantineProperties(Duration.ofDays(30), queueCapacity = 2))
        val entry = QuarantineEntry("a", "A-1", "DLX", ExcludedValue.INVENTORY, "재고가 없다", null)

        // 1개는 쓰는 중, 2개는 큐, 나머지 3개는 버려진다
        recorder.record(listOf(entry))
        started.await(5, TimeUnit.SECONDS)
        repeat(5) { recorder.record(listOf(entry)) }
        release.countDown()

        assertThat(recorder.droppedBatches()).isEqualTo(3)
        recorder.shutdown()
    }

    @Test
    fun `큐 상한이 1 미만이면 설정을 만들 수 없다`() {
        org.assertj.core.api.Assertions.assertThatThrownBy { QuarantineProperties(Duration.ofDays(30), queueCapacity = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
