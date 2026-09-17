---
id: 0066
title: 연결 타임아웃을 호출 타임아웃과 따로 두고 모든 어댑터가 한 함수로 WebClient 를 만들기로 결정
status: accepted
date: 2026-09-17
---

# 0066. 연결 타임아웃을 호출 타임아웃과 따로 두고 모든 어댑터가 한 함수로 WebClient 를 만들기로 결정

## Context

요구사항은 외부 호출의 타임아웃을 연결과 응답으로 나눠 적는다.

지금은 호출마다 `Mono.timeout` 하나만 건다([ADR-0039](0039-catalog-sync-remaining.md), [ADR-0045](0045-search-concurrency-and-budget.md)). 이 값은 연결부터 응답까지 전체에 걸린다.
연결 수립에만 거는 설정은 없어서 Reactor Netty 기본값이 쓰인다.

어댑터 둘이 WebClient 를 각자 `WebClient.builder()` 로 만든다 (`SupplierAAdapter`, `SupplierBAdapter`). 설정을 하나 더하려면 어댑터마다 고쳐야 하고, 새 어댑터가 빠뜨려도 드러나지 않는다.

확인한 사실

| 사실 | 원문 |
|---|---|
| Reactor Netty 의 연결 타임아웃 기본값은 30초다 | [Reactor Netty, HTTP Client](https://github.com/reactor/reactor-netty/blob/main/docs/modules/ROOT/pages/http-client.adoc) 절 "Connection Timeout": "If the connection establishment attempt to the remote peer does not finish within the configured connect timeout (resolution: ms), the connection establishment attempt fails. Default: 30s." |
| AWS SDK 는 연결 타임아웃 기본값을 모드별로 두고, `standard` 모드는 3100ms, 같은 리전 모드는 1100ms 다 | [AWS SDKs and Tools, Smart configuration defaults](https://docs.aws.amazon.com/sdkref/latest/guide/feature-smart-config-defaults.html) 의 표: "connectTimeoutInMillis \| 3100 \| 1100 \| 3100 \| 30000" (standard, in-region, cross-region, mobile) |
| AWS SDK 는 호출 한 번의 타임아웃이 HTTP 클라이언트 타임아웃(연결 포함)보다 길어야 한다고 적는다 | [AWS SDK for Java 2.x, Configure timeouts](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/timeouts.html) 절 "Configuration rules": "API call attempt timeout ≥ HTTP client timeouts" |

세 원문은 2026-09-17 에 열어 위 문장을 대조했다.

## Options

연결 타임아웃
- **(가) 호출 타임아웃과 따로 둔다** ← 채택 — 연결 자체가 안 되는 공급사를 호출 타임아웃까지 기다리지 않는다. 대신 설정값이 공급사마다 하나 는다
- **(나) 지금처럼 호출 타임아웃 하나로 둔다** — 기다리는 쪽에서는 어느 단계에서 늦었든 같은 대기다. 대신 연결이 안 되는 공급사도 호출 타임아웃까지 기다린다

WebClient 를 만드는 곳
- **(ㄱ) `supplier` 패키지의 함수 하나로 만든다** ← 채택 — 연결 설정이 한 곳에 있고 새 어댑터가 빠뜨릴 수 없다
- **(ㄴ) 어댑터마다 만든다** — 지금 구조다. 설정이 늘 때마다 어댑터 수만큼 고친다

## Decision

- 공급사 설정에 `connect-timeout` 을 둔다. 없으면 앱이 기동에 실패한다
- 값은 3.1초. AWS SDK `standard` 모드의 기본값을 따랐다. 공급사가 같은 리전에 있는지 모르므로 같은 리전 값(1.1초)은 쓰지 않았다.
  2026-09-17 재고·요금 호출 타임아웃을 2초로 줄이면서 이 관계를 지키려고 1.1초로 바꿨다 ([ADR-0068](0068-search-values-from-relations.md))
- 연결 타임아웃은 목록·재고 호출 타임아웃보다 짧아야 하고, 설정 객체가 검사한다. 길면 호출 타임아웃이 먼저 발동해 연결 타임아웃이 하는 일이 없다
- 모든 어댑터는 `supplierWebClient(config, apiKeyHeader)` 로 WebClient 를 만든다
- 연결 타임아웃으로 실패한 것은 시간 초과로 표시하고 일시적인 실패로 보지 않는다. Netty 의 연결 타임아웃은 이미 그렇게 분류하고 있었다 ([ADR-0051](0051-retry-transient-supplier-failures.md))

## Consequences

**얻는 것**
- 연결이 안 되는 공급사는 연결 타임아웃(지금 1.1초) 안에 실패한다. 재고 호출 타임아웃이나 목록 호출 타임아웃(30초)까지 기다리지 않는다
- WebClient 설정을 바꿀 곳이 한 곳이다

**잃는 것**
- 공급사 설정값이 하나 는다
- 연결 타임아웃 값은 우리 공급사를 재서 정한 값이 아니다. 공급사가 멀리 있어 연결에 오래 걸리면 정상 공급사도 실패할 수 있다

**다시 볼 것**
- 실제 공급사의 연결 시간을 재면 그 값으로 바꾼다
- 커넥션 풀에서 연결을 기다리는 시간(기본 45초)은 이 결정이 다루지 않는다

## Discussion

- **AI 주장과 근거** — 독립 검토가 "요구사항은 연결·응답 타임아웃을 적는데 연결 타임아웃이 없다"고 지적했다. AI 가 (가)·(ㄱ)을 권했다
- **사용자 판단** — (가)로 정했다
- **값** — AI 가 AWS SDK 문서의 기본값을 근거로 3.1초를 적었다

## Verification

연결이 수립되지 않으면 호출 타임아웃까지 기다리지 않고 실패하는지
  → `SupplierAvailabilityAdapterTest.연결이 수립되지 않으면 호출 타임아웃까지 기다리지 않고 연결 타임아웃에서 실패한다`
  (연결 타임아웃 설정을 지우면 이 테스트가 5초를 기다리고 실패하는 것을 2026-09-17 에 확인했다)

연결 타임아웃이 호출 타임아웃보다 길면 앱이 기동에 실패하는지
  → `StayPropertiesTest.연결 타임아웃이 호출 타임아웃보다 길면 만들 수 없다`
