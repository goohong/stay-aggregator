package com.stayaggregator.quarantine

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 격리 기록 테이블을 아는 유일한 곳 (ADR-0059).
 *
 * 같은 문제가 다시 오면 새 행을 만들지 않고 그 행을 갱신한다 (ADR-0055).
 * 횟수는 DB 가 한 문장 안에서 더한다. 여러 검색이 같은 행을 동시에 갱신해도 갱신이 유실되지 않게 하려는 것이다.
 */
@Repository
class QuarantineRepository(private val jdbcClient: JdbcClient) {

    fun record(entry: QuarantineEntry, sourceJson: String?) {
        jdbcClient.sql(
            """
            insert into quarantine_record (
                supplier, hotel_code, room_type_code, excluded_value, record_kind,
                occurrences, first_seen, last_seen, last_reason, last_source
            )
            values (:supplier, :hotelCode, :roomTypeCode, :value, :kind, 1, now(), now(), :reason, cast(:source as jsonb))
            on conflict on constraint uk_quarantine_problem
            do update set occurrences = quarantine_record.occurrences + 1,
                          last_seen   = excluded.last_seen,
                          last_reason = excluded.last_reason,
                          last_source = excluded.last_source
            """.trimIndent(),
        )
            .param("supplier", entry.supplierId)
            .param("hotelCode", entry.hotelCode)
            .param("roomTypeCode", entry.roomTypeCode)
            .param("value", entry.value.name)
            .param("kind", entry.kind.name)
            .param("reason", entry.reason)
            .param("source", sourceJson)
            .update()
    }

    /** 마지막으로 본 시각이 [cutoff] 보다 오래된 행을 지운다. 지운 행 수를 돌려준다 */
    fun deleteNotSeenSince(cutoff: Instant): Int =
        jdbcClient.sql("delete from quarantine_record where last_seen < :cutoff")
            .param("cutoff", java.sql.Timestamp.from(cutoff))
            .update()
}
