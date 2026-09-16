---
id: 0039
title: 목록 동기화 구현 단위의 나머지 결정
status: accepted
date: 2026-09-16
---

# 0039. 목록 동기화 구현 단위의 나머지 결정

구현 단위 하나에 걸린 작은 결정들을 한 문서에 묶는다. 결정마다 합의한 날짜를 소제목에 적는다.

## Context

[ADR-0038](0038-catalog-sync-execution.md) 까지로 목록 동기화의 큰 결정은 끝났다. 구현 구조를 다른 모델의 에이전트에게 검토시킨 결과
결정과 어긋나는 곳 세 가지가 나왔고, 그중 둘은 여기서 정해야 할 갈림길이었다.

- 계획에 타임아웃이 없었다. Mock 의 무응답은 10분간 붙잡고 스케줄러는 기본 스레드 하나라("The ThreadPoolTaskScheduler uses one thread by default",
  [Boot, Task Execution and Scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)),
  공급사 하나가 멈추면 다른 공급사와 이후 주기까지 멈춘다. [ADR-0019](0019-first-catalog-failure-no-special-handling.md) 와 어긋난다
- 뺀 항목을 무엇으로 남길지가 비어 있었다. 기록의 형태는 Q17·Q18 로 미뤄 둔 상태다
- Mock 의 숙소 목록 API 에는 모드가 없어 [ADR-0037](0037-missing-catalog-entries-kept-and-marked.md)·ADR-0038 의 Verification 을 확인할 수 없었다
- 테스트 사이에 DB 를 어떻게 되돌릴지는 [ADR-0035](0035-compose-and-testcontainers.md) 가 미뤄 두었다

## Decision

### 목록 조회 타임아웃을 어디에 둘지 (9/16)

공급사별 설정 묶음 안에 둔다([ADR-0038](0038-catalog-sync-execution.md) 이 정한 그 묶음이다). 코드에 기본값을 두지 않고, 설정에 값이 없으면 앱이 뜨지 않게 한다.

- **이유** — 공급사마다 응답 속도가 다르면 느린 쪽 때문에 빠른 쪽의 실패 감지가 늦어진다. 어댑터가 이미 자기 설정 묶음을 받으므로 타임아웃도 같은 자리에 있다
- 공급사가 늘어 같은 값이 반복되면 "공통 기본값 + 공급사별 덮어쓰기"로 옮긴다. 연동 클라이언트들이 쓰는 형태다
  ([Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html) 의 `default` 이름, [Resilience4j](https://resilience4j.readme.io/docs/getting-started-3) 의 공유 설정)

### 목록 조회 타임아웃 값 (9/16)

**30초**로 둔다. 이 값은 다시 볼 것으로 남긴다.

확인한 사실

| 사실 | 원문 |
|---|---|
| 흔히 쓰는 중간 장비의 기본 읽기·유휴 한계가 60초 안팎이다 | [nginx](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout): "Default: proxy_read_timeout 60s;" · [AWS ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/APIReference/API_LoadBalancerAttribute.html): "The default is 60 seconds." · [Cloudflare](https://developers.cloudflare.com/fundamentals/reference/connection-limits/): 읽기 125초 |
| 어떤 공급사는 자기 API 에 90초를 권한다. 다만 연결 타임아웃 맥락이고, 쇼핑 호출에는 더 짧게 잡아도 된다고 구분한다 | [Expedia Rapid](https://developers.expediagroup.com/rapid/lodging/reference/error-responses): "for Lodging and our other APIs, we recommend 90 seconds" / "you may decide to use a smaller connection time out for Shopping availability calls" |
| 우리가 쓰는 `Mono.timeout()` 은 연결부터 응답까지 전체에 걸린다. 커넥션 풀에서 연결을 기다리는 시간은 그 바깥이다(기본 45초) | [Reactor Netty, HttpClient Timeout](https://github.com/reactor/reactor-netty/blob/main/docs/modules/ROOT/pages/http-client.adoc): "the `timeout` operator can only apply to the operation as a whole, from establishing the connection to the remote peer to receiving the response" |
| 스케줄러는 기본 스레드 하나라, 멈춘 공급사가 있으면 기동 후 매핑 준비가 공급사 수만큼 늦어진다 | [Spring Boot](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html): "The ThreadPoolTaskScheduler uses one thread by default" |

- **이유** — 90초를 걸어도 중간 장비가 대개 60초 안팎에서 먼저 끊으므로 그 값이 하는 일이 없다. 30초는 우리가 정한 한계가 되고, 단일 스레드에서 다른 공급사를 막는 시간도 줄어든다.
  목록 동기화는 하루 한 번 도는 배경 작업이라 30초 안에 오지 않는 응답을 기다릴 이유가 없다
- **30초라는 숫자 자체는 문서에 없다.** "중간 장비 기본값보다 짧게, 배경 작업이므로 여유 있게"라는 우리 판단이다
- Mock 은 같은 컴퓨터에서 고정 응답을 주어 1.2~1.6ms 였다. 이 측정으로는 값을 정할 수 없어 위 근거를 대신 썼다
- 어떤 공급사가 대량 수집 호출에 5분을 권하는 문서가 있으나, 그것은 예약 수집 엔드포인트에 대한 것이라 근거에서 뺐다
- **다시 볼 것** — 실제 공급사를 붙이면 관측한 지연 분포로 정한다. 공급사가 목록을 쪽 단위로 나눠 주면 호출 1회 타임아웃과 동기화 전체 예산을 따로 두어야 한다

### 스펙과 달라 뺀 항목의 표시 (9/16)

[ADR-0027](0027-spec-violation-handling-criteria.md) 의 세 번째 질문으로 뺀 항목도 이번 목록에 없는 것으로 보아 `missing_since` 가 찍힌다.
공급사가 뺀 것인지 우리가 읽지 못한 것인지는 그 컬럼만으로 구분하지 않는다. 자세한 내용은 [ADR-0037](0037-missing-catalog-entries-kept-and-marked.md) 에 적었다.

### Mock 의 숙소 목록 API 에도 모드 적용 (9/16)

목록 API 에도 장애·무응답·지연 모드를 건다. 모드는 그 공급사의 두 API 에 함께 적용된다.

- **이유** — 이것이 없으면 "응답을 읽지 못한 동기화가 표시를 건드리지 않는지"(ADR-0037), "무응답 공급사가 있어도 기동이 끝나는지"(ADR-0038), 타임아웃 값 측정을 모두 확인할 수 없다
- 요구사항은 Mock 에 시간을 쓰지 말라고 하면서도 연동 동작을 검증하는 수단으로는 확인한다고 한다. 이건 검증 수단 쪽이고, 이미 있는 모드 처리 함수를 재사용한다

### 뺀 것을 지금 어디까지 남길지 (9/16)

정규화는 "쓸 것"과 "뺀 것"을 함께 돌려준다. 동기화는 공급사마다 한 줄을 로그로 남긴다(반영 건수, 제외 건수, 이번 목록에 없는 숙소 수).
뺀 응답의 원본을 따로 보관하는 것은 하지 않는다.

- **"이번 목록에 없는 숙소 수"는 반영이 끝난 뒤에 센다.** 반영은 그 공급사 행을 먼저 모두 표시하고 이번 목록에 있는 것을 되살리는 순서라(ADR-0037),
  표시하는 동안의 건수를 세면 목록이 그대로여도 전체 행 수가 나온다. 처음 구현이 그 값을 로그에 썼고 실행 중에 드러났다
  → `CatalogSyncIntegrationTest.반영 결과는 목록에 없어 표시가 남은 숙소만 센다`

- **이유** — 뺀 것을 결과값으로 돌려주면 테스트가 확인할 대상이 생긴다. 로그 한 줄은 운영의 최소치라 미정인 설계(Q17·Q18)를 지어내지 않는다
- **잃는 것** — 요구사항이 선택 항목으로 둔 "변환하지 못한 응답을 버리지 않고 따로 보관"은 아직 이행되지 않는다. 로그에는 무엇이 왜 빠졌는지까지만 남는다

### 최대 수용 인원이 1 미만인 객실 타입 (9/16)

목록에 최대 수용 인원이 `0` 이나 음수로 오면 그 객실 타입을 뺀다. [ADR-0027](0027-spec-violation-handling-criteria.md) 의 세 번째 질문에 걸리는 경우로 본다.

- **이유** — 한 명도 묵을 수 없는 객실 타입은 팔 수 없다. 재고 수가 음수일 때를 "필드가 객실 수로 정의돼 있다"는 데서 끌어내 판단한 것과 같은 기준이다
- 이 판단을 재고 필드 밖으로 넓힌 것이므로 여기에 적어 둔다. 스펙이 1 이상이라고 명시하지는 않는다

### 테스트 사이에 DB 를 되돌리는 방법 (9/16)

테스트마다 매핑 테이블을 비운다.

- **이유** — 확인해야 할 동작이 커밋과 롤백 자체다(ADR-0038). 테스트를 트랜잭션으로 감싸 롤백하면 동기화의 트랜잭션이 그 안으로 합쳐져 그 동작을 가린다
- **잃는 것** — 테스트를 동시에 돌리면 서로의 데이터를 지운다. Gradle 의 테스트 병렬 실행은 기본값이 1 이라 지금은 순차다
  ([Gradle, Testing in Java projects](https://docs.gradle.org/current/userguide/java_testing.html)). 동시 실행이 필요해지면 포크마다 DB 를 나누는 쪽으로 다시 본다

## Consequences

**얻는 것**
- 공급사 하나가 응답하지 않아도 나머지 공급사와 이후 주기가 막히지 않는다
- 뺀 항목을 테스트가 단언할 수 있고, 운영에서는 로그로 원인을 찾는다
- ADR-0037·0038 의 Verification 을 Mock 으로 실제 확인할 수 있다

**잃는 것**
- 타임아웃 값이 설정에 없으면 앱이 뜨지 않는다. 처음 띄우는 사람이 설정을 채워야 한다
- 뺀 응답의 원본은 남지 않는다
- 테스트를 동시에 돌릴 수 없다

## Discussion

- **AI 주장과 근거** — 구조 계획에 타임아웃을 넣지 않았다. ADR-0038 이 값을 측정 뒤로 넘겼기 때문에 그 단계에서 함께 넣으면 된다고 보았다
- **반박** — 검토는 ADR-0038 이 넘긴 것은 값이지 존재 여부가 아니라고 지적했다. 스케줄러 스레드가 하나라 무응답 하나가 전체를 멈춘다는 점도 함께 짚었다
- **검증 결과** — 지적이 맞다. 인용한 Boot 문서를 원문과 대조했다. 값이 정해지기 전에도 타임아웃 자리는 있어야 하고, 추측한 기본값을 두지 않으려면 설정을 필수로 두는 방법이 맞았다
- **표현 정정** — 이 단위를 논의하며 "규약 위반"이라는 번역체를 "스펙과 다른 응답", "값을 정할 수 없는 항목"으로 바꿨다.
  "격리"는 요구사항의 선택 항목 이름이라 그 항목을 가리킬 때만 쓰고, 평소에는 "버리지 않고 따로 보관"으로 쓴다

## Verification

공급사별 설정에 타임아웃 값이 있고 코드에 기본값이 없는지. Mock 을 지연 모드로 두었을 때 그 공급사만 실패하고 다른 공급사의 매핑은 반영되는지.
정규화 결과가 쓸 것과 뺀 것을 함께 돌려주는지. 테스트가 매핑 테이블을 비우고 시작하는지. 테스트를 만들면 그 이름을 여기에 적는다.
