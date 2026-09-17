package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.FileSystemResource
import java.time.Duration

/**
 * 설정끼리의 관계를 설정 객체가 지키는지 본다 (ADR-0041, ADR-0068).
 */
class StayPropertiesTest {

    @Test
    fun `호출 타임아웃이 검색 전체 타임아웃보다 길면 만들 수 없다`() {
        // 검색 전체 타임아웃으로 취소된 chunk 는 서킷이 세지 않아, 이 관계가 깨지면 무응답 공급사에 서킷이 열리지 않는다 (ADR-0056)
        assertThatThrownBy { properties(availabilityTimeout = Duration.ofSeconds(8), timeout = Duration.ofSeconds(8)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("검색 전체 타임아웃")
    }

    @Test
    fun `재시도까지 다 쓴 시간이 검색 전체 타임아웃을 넘으면 만들 수 없다`() {
        // 2.6초 × 3 + 대기 최대 0.45초 = 8.25초. 호출 하나는 8초보다 짧아도 재시도까지 거치면 넘는다 (ADR-0068)
        assertThatThrownBy { properties(availabilityTimeout = Duration.ofMillis(2_600), timeout = Duration.ofSeconds(8)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("재시도까지 다 쓰면")
    }

    @Test
    fun `요청 한도 초과 재시도까지 다 쓴 시간이 검색 전체 타임아웃을 넘으면 만들 수 없다`() {
        // 2초 × 3 + 1.5초 + 1.5초 = 9초. 요청 한도 초과를 두 번 재시도하면 넘는다. 한 번이면 5.5초다 (ADR-0068 의 관계식 ③)
        assertThatThrownBy {
            properties(Duration.ofSeconds(2), Duration.ofSeconds(8), throttledRetry = StayProperties.RetryPolicy(2, Duration.ofSeconds(1), Duration.ofMillis(1_500)))
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("요청 한도 초과 재시도까지 다 쓰면")
        assertThat(StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(1_500)).worstCase(Duration.ofSeconds(2)))
            .isEqualTo(Duration.ofMillis(5_500))
    }

    @Test
    fun `재시도까지 다 쓴 시간의 상한은 Reactor 가 대기에 무작위를 섞는 방식대로 계산한다`() {
        // 대기 i 번째는 min(최대 백오프, 1.5 × min(최소 백오프 × 2^i, 최대 백오프)) 이하다
        // 100ms·1s 로 두 번: 150ms + 300ms. 호출 2초 세 번과 더해 6.45초 (ADR-0068 의 관계식)
        assertThat(StayProperties.RetryPolicy(2, Duration.ofMillis(100), Duration.ofSeconds(1)).worstCase(Duration.ofSeconds(2)))
            .isEqualTo(Duration.ofMillis(6_450))
        // 최대 백오프가 무작위를 자른다. 1초·1초면 더할 무작위가 없다
        assertThat(StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofSeconds(1)).worstCase(Duration.ofSeconds(2)))
            .isEqualTo(Duration.ofSeconds(5))
        assertThat(StayProperties.RetryPolicy(0, Duration.ofMillis(100), Duration.ofSeconds(1)).worstCase(Duration.ofSeconds(2)))
            .isEqualTo(Duration.ofSeconds(2))
    }

    @Test
    fun `연결 타임아웃이 호출 타임아웃보다 길면 만들 수 없다`() {
        // 호출 타임아웃이 먼저 발동해 연결 타임아웃이 하는 일이 없다 (ADR-0066)
        assertThatThrownBy { StayProperties.Supplier("http://localhost", "k", Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(2)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("연결 타임아웃")
    }

    @Test
    fun `재시도 최대 백오프가 최소 백오프보다 짧으면 만들 수 없다`() {
        assertThatThrownBy { StayProperties.RetryPolicy(2, Duration.ofSeconds(1), Duration.ofMillis(100)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("재시도 최대 백오프가 최소 백오프보다 짧다")
    }

    @Test
    fun `운영 설정 파일의 값이 ADR-0068 에서 정한 값이고 설정 객체의 검사를 통과한다`() {
        // 테스트 클래스패스의 application.yml 은 테스트용 값이라, 운영 설정 파일을 경로로 직접 읽는다
        val stay = production()

        assertThat(stay.search.timeout).isEqualTo(Duration.ofSeconds(8))
        assertThat(stay.search.concurrencyPerSupplier).isEqualTo(4)
        assertThat(stay.search.retry).isEqualTo(StayProperties.RetryPolicy(2, Duration.ofMillis(100), Duration.ofSeconds(1)))
        assertThat(stay.search.throttledRetry).isEqualTo(StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(1_500)))
        assertThat(stay.search.circuitBreaker).isEqualTo(StayProperties.CircuitBreaker(50f, 20, 8, Duration.ofSeconds(30), 4))
        assertThat(stay.suppliers.values).allSatisfy { supplier ->
            assertThat(supplier.availabilityTimeout).isEqualTo(Duration.ofSeconds(2))
            assertThat(supplier.connectTimeout).isEqualTo(Duration.ofMillis(1_100))
        }
        // 반열림 상태에서 허용하는 호출 수가 동시 호출 수보다 적으면 시험 검색의 첫 chunk 일부가 거절된다 (ADR-0068)
        assertThat(stay.search.circuitBreaker.permittedNumberOfCallsInHalfOpenState).isGreaterThanOrEqualTo(stay.search.concurrencyPerSupplier)
    }

    private fun production(): StayProperties {
        val sources = YamlPropertySourceLoader().load("production", FileSystemResource("src/main/resources/application.yml"))
        val binder = Binder(ConfigurationPropertySources.from(sources), null, ApplicationConversionService.getSharedInstance())
        return binder.bind("stay", StayProperties::class.java).get()
    }

    private fun properties(
        availabilityTimeout: Duration,
        timeout: Duration,
        throttledRetry: StayProperties.RetryPolicy = StayProperties.RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(1_500)),
    ) =
        StayProperties(
            suppliers = mapOf("a" to StayProperties.Supplier("http://localhost", "k", Duration.ofSeconds(30), availabilityTimeout, Duration.ofMillis(500))),
            search = StayProperties.Search(
                timeout = timeout,
                concurrencyPerSupplier = 4,
                retry = StayProperties.RetryPolicy(2, Duration.ofMillis(100), Duration.ofSeconds(1)),
                throttledRetry = throttledRetry,
                circuitBreaker = StayProperties.CircuitBreaker(50f, 20, 8, Duration.ofSeconds(30), 4),
            ),
        )
}
