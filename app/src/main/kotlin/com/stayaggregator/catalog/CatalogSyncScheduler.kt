package com.stayaggregator.catalog

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 기동 시 한 번, 그 뒤로는 설정한 주기마다 목록 동기화를 돌린다 (ADR-0013, ADR-0016).
 *
 * 스케줄러 스레드에서 돌기 때문에 앱 기동을 막지 않는다 (ADR-0038).
 * 주기는 앞선 실행이 끝난 시점부터 재므로 두 실행이 겹치지 않는다.
 *
 * 설정이 없으면 기동 직후 한 번(ADR-0013) 돌고 하루 한 번(ADR-0016) 돈다. 그 둘이 결정된 기본값이라 여기에 적는다.
 * 타임아웃처럼 값을 추측할 수 없는 설정과 달리(ADR-0039) 이 둘은 값이 정해져 있다.
 *
 * 테스트에서는 컨텍스트가 뜨자마자 동기화가 돌아 테스트의 DB 작업과 겹치므로, 설정으로 이 빈을 끈다.
 *
 * 주기가 [MIN_INTERVAL] 보다 짧으면 앱이 뜨지 않는다 (ADR-0061). 목록은 자주 바뀌지 않는데 짧은 주기는 공급사와 DB 에 부담만 준다.
 */
@Component
@ConditionalOnProperty(name = ["stay.catalog.sync.enabled"], matchIfMissing = true)
class CatalogSyncScheduler(
    private val catalogSyncService: CatalogSyncService,
    @Value("\${stay.catalog.sync.interval:PT24H}") interval: Duration,
) {
    init {
        require(interval >= MIN_INTERVAL) { "목록 갱신 주기($interval)가 최소 간격($MIN_INTERVAL)보다 짧다" }
    }

    @Scheduled(
        initialDelayString = "\${stay.catalog.sync.initial-delay:PT0S}",
        fixedDelayString = "\${stay.catalog.sync.interval:PT24H}",
    )
    fun sync() {
        catalogSyncService.syncAll()
    }

    companion object {
        /** 초·분 단위 실수를 막는 선이다. 측정한 값이 아니라 목적에서 고른 값이다 (ADR-0061) */
        val MIN_INTERVAL: Duration = Duration.ofHours(1)
    }
}
