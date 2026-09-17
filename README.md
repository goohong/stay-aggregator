# stay-aggregator

여러 외부 숙박 공급사의 서로 다른 API 를 하나의 표준 모델로 통합해, 날짜와 인원으로 검색한 결과를 돌려주는 연동 백엔드입니다.

공급사마다 API 가 다릅니다. 어떤 곳은 날짜별 1박 요금을 주고 어떤 곳은 숙박 기간 전체의 총액만 줍니다.
실패를 HTTP 상태 코드로 알리는 곳도 있고, 항상 200 을 주면서 본문의 코드로만 알리는 곳도 있습니다.
이 차이를 흡수해 고객에게는 같은 형식의 결과를 주는 것이 이 저장소가 하는 일입니다.

## 요구 환경

| | |
|---|---|
| JDK | 25 ([ADR-0022](docs/adr/0022-jvm-25.md)) |
| Docker | 로컬 PostgreSQL 과 테스트용 컨테이너에 필요 ([ADR-0035](docs/adr/0035-compose-and-testcontainers.md)) |

Gradle 은 저장소에 포함된 래퍼를 씁니다. 따로 설치하지 않아도 됩니다.

## 모듈

| 모듈 | 하는 일 |
|---|---|
| `app` | 연동 백엔드 |
| `mock-supplier` | 공급사 A·B 를 모사하는 별도 서버. 앱과 코드도 의존성도 공유하지 않고 HTTP 로만 만납니다 ([ADR-0005](docs/adr/0005-separate-mock-module.md)) |

## 실행

**Mock 공급사를 먼저 띄웁니다.** 앱은 기동한 뒤 공급사의 숙소 목록을 받아 매핑을 만듭니다
([ADR-0013](docs/adr/0013-catalog-sync-on-startup-and-interval.md), [ADR-0019](docs/adr/0019-first-catalog-failure-no-special-handling.md)).
PostgreSQL 은 `compose.yml` 의 것을 앱이 함께 띄웁니다.

```bash
./gradlew :mock-supplier:bootRun   # 9090 포트
./gradlew :app:bootRun             # 다른 터미널에서, 8080 포트
./gradlew :app:test                # 테스트 (Docker 필요)
```

## 검색 API

```
GET /api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-08&adults=2&children=0
```

Mock 은 요청 날짜와 상관없이 2026-10-05 ~ 10-08(3박) 재고를 고정으로 줍니다. 다른 날짜로 호출해도 같은 값이 옵니다.

체크아웃일은 숙박에 넣지 않습니다. 위 요청은 3박입니다 ([ADR-0043](docs/adr/0043-stay-period-value-object.md)).

```jsonc
{
  "checkIn": "2026-10-05", "checkOut": "2026-10-08", "nights": 3,
  "adults": 2, "children": 0,

  // 공급사마다 결과를 빠짐없이 만들었는지 알려줍니다
  "suppliers": [
    { "supplier": "a", "status": "SUCCEEDED", "failureReason": null,
      "roomTypeCount": 2, "outOfSpecCount": 0, "failedChunks": 0 },
    { "supplier": "b", "status": "FAILED",
      "failureReason": "공급사 B 가 실패를 알렸다: resultCode=E503, resultMessage=TEMPORARILY_UNAVAILABLE",
      "roomTypeCount": 0, "outOfSpecCount": 0, "failedChunks": 1 }
  ],

  "roomTypes": [
    { "hotelId": "20ac7cce-…", "hotelName": "Hangang View Hotel",
      "roomTypeId": "6a586d72-…", "roomTypeName": "Deluxe Double",
      "maxOccupancy": 2, "supplier": "a",
      "availableRooms": 2,
      "rate": { "totalAmount": 396000, "currency": "KRW", "breakfastIncluded": false } }
    // 공급사 a 의 나머지 한 건은 줄임
  ]
}
```

| 필드 | 뜻 |
|---|---|
| `status` | `FAILED` 는 **그 공급사 결과를 아예 만들지 못했다**는 뜻입니다. 매핑을 못 읽었거나, 모든 호출이 실패했거나, 검색 전체 타임아웃을 넘겼습니다 ([ADR-0050](docs/adr/0050-partial-chunk-failure.md)) |
| `outOfSpecCount` | 응답이 스펙과 달라 결과에서 뺀 객실 타입 수입니다. 0 이 아니면 그 공급사 결과가 줄어든 것입니다 ([ADR-0046](docs/adr/0046-supplier-status-in-search-response.md)) |
| `failedChunks` | 성공했지만 호출하지 못한 chunk 수입니다. 0 이 아니면 그 공급사의 일부 숙소는 이 응답에 없습니다 |
| `availableRooms` | 요청 기간 전체에 예약할 수 있는 객실 수입니다. **0 이어도 빼지 않고 내보냅니다** ([ADR-0026](docs/adr/0026-expose-unbookable-as-zero.md)) |
| `rate.totalAmount` | 세금을 포함한 **기간 전체 총액**입니다. 1박 얼마인지는 보여주는 쪽이 총액과 `nights` 로 계산합니다 ([ADR-0042](docs/adr/0042-rate-as-tax-included-total.md)) |

필드마다의 타입과 뜻, 부분 실패와 오류 응답까지는 [docs/api.md](docs/api.md) 에 있습니다.
앱을 띄운 뒤 `http://localhost:8080/swagger-ui.html` 에서 같은 계약을 보고 바로 호출해 볼 수도 있습니다. 명세는 코드에서 만듭니다 ([ADR-0063](docs/adr/0063-springdoc-openapi.md)).

입력이 조건을 어기면 400 과 그 사유가 나갑니다. 사유 문장은 값을 담는 객체가 거부하며 낸 것을 그대로 씁니다.

```bash
curl 'localhost:8080/api/v1/stays/search?checkIn=2026-10-08&checkOut=2026-10-05&adults=2&children=0'
# {"message":"체크아웃일이 체크인일보다 뒤가 아니다"}
```

## 공급사 장애를 만들어 보기

Mock 의 응답 모드를 실행 중에 바꿉니다. 그 공급사의 두 API(숙소 목록, 재고·요금)에 함께 적용됩니다.

```bash
curl -X POST 'localhost:9090/control/a/mode?value=error'                  # 장애 응답
curl -X POST 'localhost:9090/control/b/mode?value=no-response'           # 연결은 되고 응답이 없음
curl -X POST 'localhost:9090/control/a/mode?value=delay&delaySeconds=3'  # 3초 지연
curl -X POST 'localhost:9090/control/a/mode?value=normal'                # 되돌리기
```

한쪽을 장애나 무응답으로 두고 검색하면 나머지 공급사 결과가 그대로 나오고, 실패한 공급사가 `suppliers` 에 드러납니다.

## 무엇을 표준으로 삼고 무엇을 버렸는가

공급사가 주는 정보가 서로 겹치지 않아, 하나로 모으려면 버리는 것이 생깁니다. 버린 것과 그 이유입니다.

| 버린 것 | 왜 | |
|---|---|---|
| **날짜별 1박 금액** | 한 공급사는 날짜별로, 다른 공급사는 총액만 줍니다. 날짜별에서 총액은 만들 수 있지만 총액에서 날짜별은 되돌릴 수 없습니다. 두 공급사가 같은 형식이 되는 기준은 총액뿐입니다 | [ADR-0042](docs/adr/0042-rate-as-tax-included-total.md) |
| **세금액의 내역** | 한쪽은 날짜별 세금액을, 다른 쪽은 포함 여부만 줍니다. 세금을 나눠 담으면 같은 문제가 세금 쪽에서 되풀이됩니다 | 같은 곳 |
| **재고·요금 응답의 숙소명·객실 타입명** | 두 API 가 모두 이름을 주는데, 자주 바뀌지 않는 목록 쪽 값을 저장해 두고 그것을 씁니다. 검색 응답마다 이름이 달라지지 않습니다 | [ADR-0012](docs/adr/0012-static-info-from-catalog.md) |
| **통화 환산** | 받은 통화를 그대로 싣습니다. 환율을 우리가 정하면 그 값이 사실처럼 나갑니다. 통화가 다른 상품은 섞여 나가고 항목마다 통화가 붙습니다 | [ADR-0042](docs/adr/0042-rate-as-tax-included-total.md), [ADR-0053](docs/adr/0053-mixed-currency-exposure.md) |

한 줄로 묶으면 **추정한 값을 사실처럼 내보내지 않는다**입니다. 되돌릴 수 없는 계산, 근거 없는 환산, 이름으로 하는 추측을 모두 같은 이유로 하지 않습니다.

## 새 공급사를 붙이려면

공급사 C 를 붙일 때 고치는 곳은 **셋**입니다. `catalog`·`search`·`mapping` 패키지는 건드리지 않습니다.

| 만들거나 고칠 것 | 내용 |
|---|---|
| `app/.../supplier/c/SupplierCAdapter.kt` | `SupplierAdapter` 를 구현합니다. 목록 조회와 재고·요금 조회 둘 다입니다. 하나만 구현하면 기동 검사와 ArchUnit 테스트가 실패합니다 ([ADR-0072](docs/adr/0072-adapter-http-helper-and-archunit.md)) |
| `app/.../supplier/c/SupplierC*Response.kt` | 그 공급사의 응답을 받는 DTO 둘. 스펙의 필드를 모두 받기만 하고 검증하지 않습니다 ([ADR-0030](docs/adr/0030-supplier-response-dto-receives-without-validation.md)) |
| `application.yml` 의 `stay.suppliers.c` | 주소·인증 키·타임아웃 두 개 |

consumer 는 어댑터를 인터페이스 목록으로 주입받아, 공급사가 늘어도 호출하는 쪽 코드가 그대로입니다
([ADR-0031](docs/adr/0031-supplier-adapter-boundaries.md)).
실패 표현의 차이, 필드 이름, 요금 구조는 어댑터가 흡수하고, 값을 쓸 수 있는지 검증하는 규칙은 어댑터 밖 한 곳에 있습니다.
그래서 공급사가 늘어도 검증 규칙이 공급사마다 달라지지 않습니다.

공급사 식별자는 어댑터의 상수 한 곳에만 적습니다. 응답 형태에는 싣지 않습니다 ([ADR-0049](docs/adr/0049-supplier-id-on-adapter-only.md)).

## 숙소 목록과 재고·요금은 성격이 다르다

공급사가 주는 두 가지는 바뀌는 속도가 다릅니다. 그래서 다루는 방법도 다릅니다.

**숙소 목록은 드물게 바뀌므로 저장합니다.**

- 앱을 띄울 때 한 번 받고, 그 뒤로는 앞선 실행이 끝난 시점부터 24시간마다 다시 받습니다 ([ADR-0013](docs/adr/0013-catalog-sync-on-startup-and-interval.md), [ADR-0016](docs/adr/0016-catalog-sync-interval-default-daily.md)). 주기는 설정으로 바꾸되 1시간보다 짧으면 앱이 기동에 실패합니다 ([ADR-0061](docs/adr/0061-catalog-sync-min-interval.md))
- 저장하는 것은 공급사 코드와 내부 식별자의 **매핑**뿐입니다. 요금·재고의 원본은 공급사에 있고, 조회하려면 숙소 코드를 우리가 알고 있어야 합니다 ([ADR-0017](docs/adr/0017-mapping-in-server-rdb.md))
- 이번 목록에 없는 숙소는 지우지 않고 `missing_since` 에 그 시각을 적어 검색 대상에서 뺍니다. 다시 나타나면 같은 내부 식별자로 값을 비웁니다 ([ADR-0037](docs/adr/0037-missing-catalog-entries-kept-and-marked.md))
- 응답의 숙소명·객실 타입명은 이 저장값을 씁니다 ([ADR-0012](docs/adr/0012-static-info-from-catalog.md)). 검색 때 받은 이름이 저장값과 다르면 항목은 그대로 내보내고 경고로만 남겨, 교정은 다음 동기화에 맡깁니다 ([ADR-0014](docs/adr/0014-detect-drift-in-search-correct-in-sync.md), [ADR-0062](docs/adr/0062-name-mismatch-warning.md))

**재고·요금은 매번 바뀌므로 저장하지 않습니다.**

- 검색마다 공급사에 물어봅니다. 공급사가 한 번에 숙소 코드 50개까지만 받으므로 50개씩 **chunk** 로 나눠 호출하고, 한 공급사에 동시에 보내는 chunk 수를 제한합니다. 숙소 3,000개면 공급사당 60번입니다 ([ADR-0045](docs/adr/0045-search-concurrency-and-budget.md))
- 같은 조건의 검색이 반복될 때를 위한 캐시는 숙소 단위로 Redis 에 두기로 정했고 아직 구현하지 않았습니다 ([ADR-0065](docs/adr/0065-availability-cache-redis.md))

## 견고성

| | 어떻게 | |
|---|---|---|
| 병렬 호출 | 공급사들을 동시에 호출하고, 한 공급사 안에서도 chunk 를 동시에 호출합니다. 동시에 보내는 수는 공급사당 4 로 제한합니다 | [ADR-0045](docs/adr/0045-search-concurrency-and-budget.md) |
| 타임아웃 | 호출 하나마다, 그리고 검색 한 건 전체에 겁니다. 연결 수립에는 따로 짧은 타임아웃을 걸어, 연결이 안 되는 공급사를 호출 타임아웃까지 기다리지 않습니다([ADR-0066](docs/adr/0066-connect-timeout-in-shared-webclient.md)). 호출 타임아웃은 검색 전체 타임아웃보다 짧아야 하고, 설정이 그걸 검사합니다. 목록 동기화는 백그라운드 작업이라 다른 값을 씁니다 | [ADR-0039](docs/adr/0039-catalog-sync-remaining.md), ADR-0045 |
| 부분 실패 | 공급사 하나가 실패해도 나머지로 응답하고 그 사실을 응답에 싣습니다 | [ADR-0046](docs/adr/0046-supplier-status-in-search-response.md) |
| 실패 분류 통일 | HTTP 상태로 알리는 실패, 본문 코드로 알리는 실패, 응답이 오지 않은 것을 모두 같은 공급사 실패 예외로 바꿉니다 | [ADR-0027](docs/adr/0027-spec-violation-handling-criteria.md) |
| 재시도 | **일시적인 실패만** 재시도합니다. 지수 백오프에 무작위를 섞습니다. 요청 한도 초과(429)는 1초 이상 기다려 한 번만 재시도합니다 | [ADR-0051](docs/adr/0051-retry-transient-supplier-failures.md), [ADR-0068](docs/adr/0068-search-values-from-relations.md) |
| 서킷 브레이커 | 공급사마다 둡니다. 재시도까지 다 실패한 chunk 가 많으면 한동안 그 공급사를 호출하지 않습니다 | [ADR-0056](docs/adr/0056-circuit-breaker-per-supplier-outside-retry.md) |
| 지표 | 공급사 호출을 공급사와 결과(성공·타임아웃·공급사 실패·서킷 열림·내부 오류)로 나눠 세고 `/actuator/metrics` 로 봅니다 | [ADR-0060](docs/adr/0060-supplier-call-metrics.md) |
| 격리 기록 | 스펙과 달라 뺀 항목을 버리지 않고 남깁니다. 같은 문제는 한 행으로 그룹화해 횟수·처음과 마지막 시각·마지막 사유와 원본을 두고, 오래된 행은 목록 동기화 때 지웁니다. 이름 불일치 경고도 같은 곳에 남깁니다 | [ADR-0055](docs/adr/0055-quarantine-grouped-in-db.md), [ADR-0062](docs/adr/0062-name-mismatch-warning.md) |

재시도·타임아웃·동시 실행 수 제한은 **이미 쓰는 Reactor 의 연산자**로, 서킷은 **resilience4j** 로 했습니다.
resilience4j 의 재시도·타임아웃도 안에서 같은 Reactor 연산자를 호출하는 것을 확인해, 옮겨도 동작이 같아 옮기지 않았습니다.
서킷은 Reactor 에 없어 완성된 라이브러리를 썼습니다 ([ADR-0057](docs/adr/0057-resilience4j-reactor-for-circuit-breaker.md), [비교 기록](docs/research/resilience-library-comparison.md)).

**타임아웃은 재시도하지 않습니다.** 타임아웃은 "공급사가 느리다"는 신호라 재시도해도 느릴 가능성이 높습니다.
반대로 503·429·연결 실패는 순간적일 수 있어 재시도합니다. 어느 쪽인지는 공급사 실패 예외가 들고 다니고,
**실제로 재시도할지는 호출하는 쪽이 정합니다** — 고객이 기다리는 검색은 재시도하고, 백그라운드에서 도는 목록 동기화는 재시도하지 않습니다.

**값은 관계식에서 정했습니다.** 재 보니 값 하나가 아니라 관계식이 나왔고, 실제 공급사의 지연 분포는 알 수 없었습니다.
그래서 값끼리 지켜야 할 관계(예: 재시도까지 다 쓴 chunk 가 검색 전체 타임아웃 안에 끝나야 서킷이 그 공급사를 셉니다)를 먼저 세우고,
남은 자리를 외부 기본값이나 판단으로 채웠습니다. 값마다 인용인지 계산인지 판단인지를 [ADR-0068](docs/adr/0068-search-values-from-relations.md) 에 적었고,
깨지면 장치가 조용히 멈추는 관계는 설정 객체가 기동할 때 검사합니다.
값을 바꿔야 할 때 무엇을 재고 어떻게 다시 계산하는지는 [docs/tuning.md](docs/tuning.md) 에 있습니다.

## 선택 항목의 구현 범위

요구사항이 선택으로 둔 항목마다 어디까지 했는지입니다. "설계만"은 결정과 설계를 문서로 남기고 코드는 두지 않은 것, "구현 전"은 결정은 끝났고 코드가 아직 없는 것입니다.

| 항목 | 범위 | 왜 | |
|---|---|---|---|
| 재시도 | **구현** | 일시적인 실패만, 검색에서만 재시도합니다. 값은 관계식에서 정했습니다 | [ADR-0051](docs/adr/0051-retry-transient-supplier-failures.md), [ADR-0068](docs/adr/0068-search-values-from-relations.md) |
| 서킷 브레이커 | **구현** | 공급사마다 하나, 재시도 바깥에서 chunk 의 최종 결과를 셉니다. 서킷은 Reactor 에 없어 resilience4j 를 썼습니다 | [ADR-0056](docs/adr/0056-circuit-breaker-per-supplier-outside-retry.md), [ADR-0057](docs/adr/0057-resilience4j-reactor-for-circuit-breaker.md) |
| 격리 | **구현** | 뺀 항목을 같은 문제끼리 그룹화해 DB 에 남깁니다. 이름 불일치 경고도 같은 기록에 둡니다 | [ADR-0055](docs/adr/0055-quarantine-grouped-in-db.md), [ADR-0062](docs/adr/0062-name-mismatch-warning.md) |
| 통화 | **설계만** | 받은 통화를 그대로 싣고 환산하지 않습니다. 이미 그렇게 도는 이유와, 환산이 필요해질 때의 방법을 적었습니다 | [ADR-0053](docs/adr/0053-mixed-currency-exposure.md) |
| 예약 대행 | **설계만** | 공급사 스펙에 예약 API 가 없어 요청·응답 형식을 지어내지 않았습니다. 결과를 모르는 예약은 같은 멱등 키로 공급사에 물어 확정하는 설계입니다 | [ADR-0064](docs/adr/0064-reservation-proxy-design-only.md) |
| 요금·재고 캐시 | **구현 전** | 숙소 단위로 정규화한 결과를 Redis 에 두고 TTL 은 60초로 정했습니다 | [ADR-0065](docs/adr/0065-availability-cache-redis.md), ADR-0068 |
| 동일 숙소(중복 상품 병합) | **구현 전** | 공통 키도 주소도 없어 숙소명으로 추정해 합치면 동명 숙소를 잘못 합칩니다. 사람이 확인한 짝만 같은 값으로 묶기로 정했습니다. 지금은 각각 내보내고 `supplier` 를 함께 싣습니다 | [ADR-0058](docs/adr/0058-same-hotel-confirmed-pairs-only.md) |
| API 문서 자동화 | **구현** | springdoc-openapi 로 코드에서 만듭니다. 앱을 띄우고 `/swagger-ui.html` 또는 `/v3/api-docs` | [ADR-0063](docs/adr/0063-springdoc-openapi.md) |

## 하지 않은 것

- **공급사 HTTP 원문 그대로의 보관.** 격리 기록의 원본은 우리가 읽어 들인 항목 하나입니다. 원문은 숙소 50개가 한 덩어리라 항목 하나를 떼기 어렵습니다 ([ADR-0055](docs/adr/0055-quarantine-grouped-in-db.md))
- **경보와 대시보드.** 지표는 세어 내보내지만, 무엇에 경보를 걸지는 설계로만 남겼습니다 ([ADR-0060](docs/adr/0060-supplier-call-metrics.md))
- **체크인일이 지난 날짜인지 검사.** 숙소의 시간대를 우리가 모릅니다. 서버 기준으로 막으면 현지로는 아직 어제인 숙소의 합법인 요청을 막게 됩니다 ([ADR-0052](docs/adr/0052-no-past-date-check.md))

## 주요 결정

결정의 근거와 버린 대안은 각 ADR 에 있습니다. 전체 목록은 [docs/adr/README.md](docs/adr/README.md) 입니다.

| 결정 | 한 줄 이유 | |
|---|---|---|
| 서버는 Spring MVC, 공급사 호출만 WebClient | 오래 기다리는 부분이 공급사 호출뿐이라 그 부분만 논블로킹으로 둔다 | [ADR-0020](docs/adr/0020-mvc-server-with-webclient.md) |
| 검색 요청은 가상 스레드에서 기다린다 | 공급사 응답을 기다리는 동안 요청이 OS 스레드를 붙잡지 않게 한다 | [ADR-0021](docs/adr/0021-block-on-virtual-threads.md) |
| 내부 식별자는 무작위 UUID | 순서와 개수가 드러나지 않고, 발급 방식을 바꿔도 기존 값에 영향이 없다 | [ADR-0011](docs/adr/0011-internal-id-random-uuid.md) |
| 연박 예약 가능 수는 날짜별 잔여 수의 최솟값 | 기간 전체를 팔려면 매일 밤 방이 한 실씩 있어야 한다 | [ADR-0023](docs/adr/0023-multi-night-availability-minimum.md) |
| 예약 불가 상품도 0 으로 노출한다 | 매진을 보여 줄지는 화면이 정할 일이라 서버가 정보를 버리지 않는다 | [ADR-0026](docs/adr/0026-expose-unbookable-as-zero.md) |
| 스펙과 다른 공급사 응답은 세 질문으로 분류한다 | 응답을 읽을 수 있나 / 계산에 쓰이나 / 값을 하나로 정할 수 있나 | [ADR-0027](docs/adr/0027-spec-violation-handling-criteria.md) |
| 값 검증은 그 값을 담는 객체의 생성자에 둔다 | 조건과 사유가 한 곳에 있고, 검증을 거치지 않은 객체가 존재할 수 없다 | [ADR-0041](docs/adr/0041-validate-in-constructors.md) |

## 문서

| 위치 | 내용 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 지금 코드의 구조. 패키지와 의존 방향, 두 흐름이 지나는 클래스, 실패가 갈리는 지점 |
| [docs/api.md](docs/api.md) | 검색 API 의 계약. 요청·응답 필드와 오류 응답 |
| [docs/adr/](docs/adr/) | 결정과 그 근거. 선택지와 버린 이유까지 |
| [docs/features/](docs/features/) | 기능이 무엇을 하는가, 아직 정하지 못한 질문 |
| [docs/research/](docs/research/) | 결정에 쓴 조사 기록. 출처와 조회일 |
| [docs/glossary.md](docs/glossary.md) | 용어의 뜻을 정하는 곳. 코드 이름·DB 이름과 나란히 |
| [JOURNAL.md](JOURNAL.md) | 날짜별 진행, 막힌 지점, AI 와 주고받은 반박 |
| [CLAUDE.md](CLAUDE.md) | 이 저장소에서 일하는 규칙 |

`CLAUDE.md` 를 저장소에 공개한 이유는 AI 와 함께 작성한 코드의 규칙도 결과물의 일부라고 보기 때문입니다([ADR-0003](docs/adr/0003-publish-claude-md.md)).
