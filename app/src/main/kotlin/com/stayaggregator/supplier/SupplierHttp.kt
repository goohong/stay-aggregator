package com.stayaggregator.supplier

import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriBuilder
import reactor.core.publisher.Mono

/**
 * 공급사 API 를 HTTP 로 호출하는 공통 조각. 어댑터가 하나씩 들고 쓴다 (ADR-0072).
 *
 * `bodyToMono → timeout → asSupplierFailure` 순서를 여기서 고정한다. 전에는 어댑터마다 메서드마다 손으로 붙였고,
 * 새 어댑터가 `.timeout()` 을 빠뜨리면 그 공급사 호출이 검색 전체 타임아웃까지 기다리고 서킷이 그 호출을 세지 못했다.
 * `.asSupplierFailure()` 를 빠뜨리면 HTTP 예외가 그대로 올라가 지표가 내부 오류로 세고 서킷이 무시했다.
 * 둘 다 컴파일·기동·테스트가 통과하는 조용한 실패였다.
 *
 * **실패 변환은 응답을 우리 형태로 바꾸기 전에 붙는다.** 그래서 이 클래스는 DTO 까지만 주고, `Fetched…` 로 바꾸는 것은 어댑터가 `.map` 으로 한다.
 * 뒤에 붙이면 변환 코드에서 난 우리 쪽 오류까지 공급사 실패가 된다 (`asSupplierFailure` 주석).
 *
 * 목록 호출과 재고·요금 호출의 타임아웃이 다르다 (ADR-0039, ADR-0045). 그래서 메서드가 둘이다.
 */
class SupplierHttp(
    private val supplierId: String,
    private val config: StayProperties.Supplier,
    auth: ExchangeFilterFunction,
) {
    private val client: WebClient = supplierWebClient(config, auth)

    /** 숙소 목록 호출. 조건 파라미터가 없다 */
    fun <T : Any> getCatalog(path: String, type: Class<T>): Mono<T> =
        get(type, config.timeout) { it.path(path) }

    /** 재고·요금 호출. 경로와 쿼리는 어댑터가 공급사 스펙대로 채운다 */
    fun <T : Any> getAvailability(type: Class<T>, uri: (UriBuilder) -> UriBuilder): Mono<T> =
        get(type, config.availabilityTimeout, uri)

    private fun <T : Any> get(type: Class<T>, timeout: java.time.Duration, uri: (UriBuilder) -> UriBuilder): Mono<T> =
        client.get()
            .uri { builder -> uri(builder).build() }
            .retrieve()
            .bodyToMono(type)
            .timeout(timeout)
            .asSupplierFailure(supplierId)
}
