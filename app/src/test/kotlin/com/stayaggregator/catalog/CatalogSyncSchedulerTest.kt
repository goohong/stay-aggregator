package com.stayaggregator.catalog

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.Duration

/**
 * 목록 갱신 주기의 최소 간격을 지키는지 본다 (ADR-0061).
 */
class CatalogSyncSchedulerTest {

    private val service = mock(CatalogSyncService::class.java)

    @Test
    fun `주기가 1시간보다 짧으면 만들 수 없다`() {
        assertThatThrownBy { CatalogSyncScheduler(service, Duration.ofMinutes(59)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("최소 간격")
    }

    @Test
    fun `주기가 1시간이면 만들어진다`() {
        assertThatCode { CatalogSyncScheduler(service, Duration.ofHours(1)) }.doesNotThrowAnyException()
    }
}
