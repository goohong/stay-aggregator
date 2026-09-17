package com.stayaggregator.supplier

import io.netty.channel.ChannelOption
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

/**
 * 공급사 하나를 호출하는 WebClient 를 만든다. 모든 어댑터가 이것으로 만든다 (ADR-0066).
 *
 * **연결 타임아웃은 여기서만 건다.** 호출 하나의 타임아웃([StayProperties.Supplier.availabilityTimeout] 등)은
 * 연결부터 응답까지 전체에 걸리고, 이 값은 그중 연결 수립에만 걸린다. 연결이 안 되는 공급사를 호출 타임아웃까지 기다리지 않는다.
 * Reactor Netty 의 기본값은 30초다.
 *
 * 어댑터마다 WebClient 를 직접 만들면 새 어댑터가 이 설정을 빠뜨려도 컴파일·기동·테스트가 모두 통과한다.
 */
fun supplierWebClient(config: StayProperties.Supplier, apiKeyHeader: String): WebClient =
    WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(supplierHttpClient(config)))
        .baseUrl(config.baseUrl)
        .defaultHeader(apiKeyHeader, config.apiKey)
        .build()

/**
 * 연결 설정을 담은 HTTP 클라이언트. WebClient 와 따로 두어 설정값이 실제로 들어갔는지를 네트워크 없이 확인할 수 있게 했다.
 * 라우팅되지 않는 주소로 확인하는 테스트는 환경에 따라 연결 타임아웃이 아닌 다른 실패로 끝날 수 있다.
 */
internal fun supplierHttpClient(config: StayProperties.Supplier): HttpClient =
    HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(config.connectTimeout.toMillis()))
