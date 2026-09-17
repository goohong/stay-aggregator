package com.stayaggregator.supplier

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

/**
 * 실패를 한 예외로 모으는 범위를 고정한다 (ADR-0027 의 첫 질문, ADR-0031).
 *
 * 어댑터 테스트는 이 범위를 가르지 못한다. 거기서 만드는 실패는 모두 실패 변환보다 위에서 나기 때문에,
 * 실패 변환 위치를 응답 변환 뒤로 옮겨도 그대로 통과한다.
 *
 * 여기서 고정하는 것은 함수의 범위뿐이다. **어댑터가 이 함수를 변환 앞에 두는지는 확인하지 않는다.**
 * 그것까지 보려면 어댑터의 변환 코드를 실패시켜야 하는데, DTO 필드가 모두 null 을 허용해(ADR-0030)
 * 그 코드에는 값을 옮기는 일밖에 없고 실패할 곳이 없다. 어댑터의 순서는 [asSupplierFailure] 의 설명과
 * 어댑터의 줄 순서로만 지켜진다. 검색용 어댑터를 붙일 때 같은 위치를 확인해야 한다.
 */
class SupplierFailureTest {

    @Test
    fun `실패 변환 위에서 난 오류는 공급사 실패가 된다`() {
        val mono = Mono.error<String>(IllegalStateException("연결하지 못했다")).asSupplierFailure("a")

        assertThatThrownBy { mono.block() }
            .isInstanceOf(SupplierResponseException::class.java)
            .hasMessageContaining("공급사 a")
            // 예외에 따라 message 가 비어 있어, 로그에서 실패 종류를 가릴 방법이 클래스 이름뿐이다
            .hasMessageContaining("IllegalStateException")
    }

    @Test
    fun `실패 변환 아래에서 난 오류는 그대로 간다`() {
        // 공급사 응답을 우리 형태로 바꾸는 코드에서 난 오류다. 공급사 실패로 보면 동기화가 원인을 남기지 않는다
        val mono = Mono.just("응답").asSupplierFailure("a").map<String> { throw IllegalStateException("변환하다 났다") }

        assertThatThrownBy { mono.block() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("변환하다 났다")
    }

    @Test
    fun `원인 예외는 보존된다`() {
        val cause = IllegalStateException("바닥에서 난 오류")

        val thrown = runCatching { Mono.error<String>(cause).asSupplierFailure("a").block() }.exceptionOrNull()

        assertThat(thrown?.cause).isSameAs(cause)
    }
}
