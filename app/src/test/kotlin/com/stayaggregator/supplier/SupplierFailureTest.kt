package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

/**
 * 실패를 한 신호로 모으는 범위를 고정한다 (ADR-0027 의 첫 질문, ADR-0031).
 *
 * 어댑터 테스트는 이 범위를 가르지 못한다. 거기서 만드는 실패는 모두 감싸기보다 위에서 나기 때문에,
 * 감싸는 자리를 응답 변환 뒤로 옮겨도 그대로 통과한다.
 *
 * 여기서 고정하는 것은 함수의 범위뿐이다. **어댑터가 이 함수를 변환 앞에 두는지는 확인하지 않는다.**
 * 그것까지 보려면 어댑터의 변환 코드를 실패시켜야 하는데, 지금 그 코드에는 실패할 자리가 없다.
 */
class SupplierFailureTest {

    @Test
    fun `감싸기 위에서 난 오류는 공급사 실패가 된다`() {
        val mono = Mono.error<String>(IllegalStateException("연결하지 못했다")).asSupplierFailure("a")

        assertThatThrownBy { mono.block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessageContaining("공급사 a")
            // 예외에 따라 message 가 비어 있어, 로그에서 실패 종류를 가릴 방법이 클래스 이름뿐이다
            .hasMessageContaining("IllegalStateException")
    }

    @Test
    fun `감싸기 아래에서 난 오류는 그대로 간다`() {
        // 공급사 응답을 우리 형태로 바꾸는 코드에서 난 오류다. 공급사 실패로 보면 동기화가 원인을 남기지 않는다
        val mono = Mono.just("응답").asSupplierFailure("a").map<String> { throw IllegalStateException("변환하다 났다") }

        assertThatThrownBy { mono.block() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("변환하다 났다")
    }

    @Test
    fun `이미 공급사 실패인 것은 다시 감싸지 않는다`() {
        val mono = Mono.error<String>(SupplierResponseException("본문 결과 코드가 실패다")).asSupplierFailure("a")

        assertThatThrownBy { mono.block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessage("본문 결과 코드가 실패다")
    }

    @Test
    fun `원인 예외는 보존된다`() {
        val cause = IllegalStateException("바닥에서 난 오류")

        val thrown = runCatching { Mono.error<String>(cause).asSupplierFailure("a").block() }.exceptionOrNull()

        assertThat(thrown?.cause).isSameAs(cause)
    }
}
