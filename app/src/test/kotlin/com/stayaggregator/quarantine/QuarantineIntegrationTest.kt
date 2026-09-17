package com.stayaggregator.quarantine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant

/**
 * 격리 기록이 같은 문제를 한 행으로 묶어 세는지, 오래된 행을 지우는지 확인한다 (ADR-0054, ADR-0055).
 */
@SpringBootTest(properties = ["stay.catalog.sync.enabled=false"])
@Import(QuarantineIntegrationTest.Containers::class)
class QuarantineIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class Containers {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18")
    }

    @Autowired
    private lateinit var repository: QuarantineRepository

    @Autowired
    private lateinit var recorder: QuarantineRecorder

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @BeforeEach
    fun clear() {
        jdbcClient.sql("truncate table quarantine_record").update()
    }

    @Test
    fun `같은 문제가 여러 번 오면 한 행에 횟수가 늘고 마지막 사유와 원본이 바뀐다`() {
        repository.record(entry(reason = "숙박일 2026-10-06 의 재고가 없다"), """{"day":6}""")
        val firstSeen = column("first_seen")
        repository.record(entry(reason = "숙박일 2026-10-07 의 재고가 없다"), """{"day":7}""")

        assertThat(rowCount()).isEqualTo(1)
        assertThat(column("occurrences")).isEqualTo(2L)
        // 날짜가 다른 같은 문제가 한 행으로 묶인다. 모양은 마지막 사유 문장에 남는다
        assertThat(column("last_reason")).isEqualTo("숙박일 2026-10-07 의 재고가 없다")
        assertThat(column("last_source").toString()).contains("7")
        assertThat(column("first_seen")).isEqualTo(firstSeen)
    }

    @Test
    fun `문제가 된 값이 다르면 다른 행이다`() {
        repository.record(entry(value = ExcludedValue.INVENTORY), null)
        repository.record(entry(value = ExcludedValue.RATE), null)

        assertThat(rowCount()).isEqualTo(2)
    }

    @Test
    fun `같은 값이라도 제외와 경고는 다른 행이다`() {
        repository.record(entry(value = ExcludedValue.HOTEL_NAME), null)
        repository.record(entry(value = ExcludedValue.HOTEL_NAME).copy(kind = RecordKind.WARNING), null)

        assertThat(rowCount()).isEqualTo(2)
    }

    @Test
    fun `코드가 비어 있는 문제끼리도 한 행으로 묶인다`() {
        repository.record(entry(hotelCode = null, value = ExcludedValue.HOTEL_CODE), null)
        repository.record(entry(hotelCode = null, value = ExcludedValue.HOTEL_CODE), null)

        assertThat(rowCount()).isEqualTo(1)
    }

    @Test
    fun `마지막으로 본 시각이 기준보다 오래된 행만 지운다`() {
        repository.record(entry(hotelCode = "OLD"), null)
        jdbcClient.sql("update quarantine_record set last_seen = now() - interval '40 days' where hotel_code = 'OLD'").update()
        repository.record(entry(hotelCode = "NEW"), null)

        val deleted = repository.deleteNotSeenSince(Instant.now().minusSeconds(30L * 24 * 3600))

        assertThat(deleted).isEqualTo(1)
        assertThat(jdbcClient.sql("select hotel_code from quarantine_record").query(String::class.java).list()).containsExactly("NEW")
    }

    @Test
    fun `기록은 뒤에서 쓰여 곧 남는다`() {
        recorder.record(listOf(QuarantineEntry("a", "A-1", "DLX", ExcludedValue.CURRENCY, "통화가 없다", mapOf("currency" to null))))

        awaitRows(1)
        assertThat(column("excluded_value")).isEqualTo("CURRENCY")
    }

    private fun entry(hotelCode: String? = "A-1", value: ExcludedValue = ExcludedValue.INVENTORY, reason: String = "재고가 없다") =
        QuarantineEntry("a", hotelCode, "DLX", value, reason, null)

    private fun rowCount(): Int = jdbcClient.sql("select count(*) from quarantine_record").query(Int::class.java).single()

    private fun column(name: String): Any? = jdbcClient.sql("select $name from quarantine_record").query().singleRow()[name]

    /** 기록이 뒤에서 쓰이므로 잠깐 기다린다 */
    private fun awaitRows(expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (rowCount() < expected && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertThat(rowCount()).isEqualTo(expected)
    }
}
