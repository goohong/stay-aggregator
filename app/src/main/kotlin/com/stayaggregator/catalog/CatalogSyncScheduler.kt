package com.stayaggregator.catalog

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 기동 시 한 번, 그 뒤로는 설정한 주기마다 목록 동기화를 돌린다 (ADR-0013, ADR-0016).
 *
 * 스케줄러 스레드에서 돌기 때문에 앱 기동을 막지 않는다 (ADR-0038).
 * 주기는 앞선 실행이 끝난 시점부터 재므로 두 실행이 겹치지 않는다.
 *
 * 테스트에서는 컨텍스트가 뜨자마자 동기화가 돌아 테스트의 DB 작업과 겹치므로, 설정으로 이 빈을 끈다.
 */
@Component
@ConditionalOnProperty(name = ["stay.catalog.sync.enabled"], matchIfMissing = true)
class CatalogSyncScheduler(private val catalogSyncService: CatalogSyncService) {

    @Scheduled(
        initialDelayString = "\${stay.catalog.sync.initial-delay}",
        fixedDelayString = "\${stay.catalog.sync.interval}",
    )
    fun sync() {
        catalogSyncService.syncAll()
    }
}
