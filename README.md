# stay-aggregator

여러 외부 숙박 공급사의 서로 다른 API 를 하나의 표준 모델로 통합해, 날짜와 인원으로 검색한 결과를 돌려주는 연동 백엔드입니다.

공급사마다 API 가 다릅니다. 어떤 곳은 날짜별 1박 요금을 주고 어떤 곳은 숙박 기간 전체의 총액만 줍니다.
실패를 HTTP 상태 코드로 알리는 곳도 있고, 항상 200 을 주면서 본문의 코드로만 알리는 곳도 있습니다.
이 차이를 흡수해 고객에게는 같은 모양의 결과를 주는 것이 이 저장소가 하는 일입니다.

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
| `mock-supplier` | 공급사 A·B 를 흉내내는 별도 서버. 앱과 코드도 의존성도 공유하지 않고 HTTP 로만 만납니다 ([ADR-0005](docs/adr/0005-separate-mock-module.md)) |

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

체크아웃일은 숙박에 넣지 않습니다. 위 요청은 3박입니다 ([ADR-0043](docs/adr/0043-stay-period-value-object.md)).

```jsonc
{
  "checkIn": "2026-10-05", "checkOut": "2026-10-08", "nights": 3,
  "adults": 2, "children": 0,

  // 공급사마다 결과가 온전한지 알려줍니다
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
| `status` | `FAILED` 는 **그 공급사 결과를 아예 만들지 못했다**는 뜻입니다. 매핑을 못 읽었거나, 모든 호출이 실패했거나, 시간 한계를 넘겼습니다 ([ADR-0050](docs/adr/0050-partial-chunk-failure.md)) |
| `outOfSpecCount` | 응답이 규약과 달라 결과에서 뺀 객실 타입 수입니다. 0 이 아니면 그 공급사 결과가 줄어든 것입니다 ([ADR-0046](docs/adr/0046-supplier-status-in-search-response.md)) |
| `failedChunks` | 성공했지만 부르지 못한 호출 묶음 수입니다. 0 이 아니면 그 공급사의 일부 숙소는 이 응답에 없습니다 |
| `availableRooms` | 요청 기간 전체에 예약할 수 있는 객실 수입니다. **0 이어도 빼지 않고 내보냅니다** ([ADR-0026](docs/adr/0026-expose-unbookable-as-zero.md)) |
| `rate.totalAmount` | 세금을 포함한 **기간 전체 총액**입니다. 1박 얼마인지는 보여주는 쪽이 총액과 `nights` 로 계산합니다 ([ADR-0042](docs/adr/0042-rate-as-tax-included-total.md)) |

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
| **날짜별 1박 금액** | 한 공급사는 날짜별로, 다른 공급사는 총액만 줍니다. 날짜별에서 총액은 만들 수 있지만 총액에서 날짜별은 되돌릴 수 없습니다. 두 공급사가 같은 모양이 되는 기준은 총액뿐입니다 | [ADR-0042](docs/adr/0042-rate-as-tax-included-total.md) |
| **세금액의 내역** | 한쪽은 날짜별 세금액을, 다른 쪽은 포함 여부만 줍니다. 세금을 나눠 담으면 같은 문제가 세금 쪽에서 되풀이됩니다 | 같은 곳 |
| **재고·요금 응답의 숙소명·객실 타입명** | 두 API 가 모두 이름을 주는데, 자주 바뀌지 않는 목록 쪽 값을 저장해 두고 그것을 씁니다. 검색 응답마다 이름이 달라지지 않습니다 | [ADR-0012](docs/adr/0012-static-info-from-catalog.md) |
| **통화 환산** | 받은 통화를 그대로 싣습니다. 환율을 우리가 정하면 그 값이 사실처럼 나갑니다 | [ADR-0042](docs/adr/0042-rate-as-tax-included-total.md) |

한 줄로 묶으면 **추정한 값을 사실처럼 내보내지 않는다**입니다. 되돌릴 수 없는 계산, 근거 없는 환산, 이름으로 하는 추측을 모두 같은 이유로 하지 않습니다.

## 새 공급사를 붙이려면

공급사 C 를 붙일 때 고치는 곳은 **셋**입니다. `catalog`·`search`·`mapping` 패키지는 건드리지 않습니다.

| 만들거나 고칠 것 | 내용 |
|---|---|
| `app/.../supplier/c/SupplierCAdapter.kt` | `SupplierAdapter` 를 구현합니다. 목록 조회와 재고·요금 조회 둘 다입니다. 하나를 빠뜨리면 컴파일되지 않습니다 |
| `app/.../supplier/c/SupplierC*Response.kt` | 그 공급사의 응답을 받는 DTO 둘. 규약의 필드를 모두 받기만 하고 판정하지 않습니다 ([ADR-0030](docs/adr/0030-supplier-response-dto-receives-without-validation.md)) |
| `application.yml` 의 `stay.suppliers.c` | 주소·인증 키·타임아웃 두 개 |

consumer 는 어댑터를 인터페이스 목록으로 주입받아, 공급사가 늘어도 호출하는 쪽 코드가 그대로입니다
([ADR-0031](docs/adr/0031-supplier-adapter-boundaries.md)).
실패 표현의 차이, 필드 이름, 요금 구조는 어댑터가 흡수하고, 값을 쓸 수 있는지 판정하는 규칙은 어댑터 밖 한 곳에 있습니다.
그래서 공급사가 늘어도 판정 규칙이 갈라지지 않습니다.

공급사 식별자는 어댑터의 상수 한 곳에만 적습니다. 응답 형태에는 싣지 않습니다 ([ADR-0049](docs/adr/0049-supplier-id-on-adapter-only.md)).

## 견고성

| | 어떻게 | |
|---|---|---|
| 병렬 호출 | 공급사들을 동시에 부르고, 한 공급사 안에서도 호출 묶음을 동시에 부릅니다 | [ADR-0045](docs/adr/0045-search-concurrency-and-budget.md) |
| 타임아웃 | 호출 하나마다, 그리고 검색 한 건 전체에 겁니다. 목록 동기화는 배경 작업이라 다른 값을 씁니다 | [ADR-0039](docs/adr/0039-catalog-sync-remaining.md), ADR-0045 |
| 부분 실패 | 공급사 하나가 실패해도 나머지로 응답하고 그 사실을 응답에 싣습니다 | [ADR-0046](docs/adr/0046-supplier-status-in-search-response.md) |
| 실패 판정 통일 | HTTP 상태로 알리는 실패, 본문 코드로 알리는 실패, 응답이 오지 않은 것을 모두 같은 신호로 바꿉니다 | [ADR-0027](docs/adr/0027-spec-violation-handling-criteria.md) |

**타임아웃 값과 동시 실행 수는 아직 임시값입니다.** 재 보니 값이 아니라 관계식이 나왔습니다 —
`검색 시간 ≈ ceil(묶음 수 ÷ 동시 실행 수) × 호출 하나의 지연`.
묶음 수는 숙소 수로 정해지므로, 숙소 규모와 실제 공급사의 지연 분포를 모르고는 숫자를 정할 수 없습니다.
그 사실을 숨기지 않고 [측정 기록](docs/research/search-latency.md)과 ADR-0045 에 적어 두었습니다.

## 하지 않은 것

- **재시도와 서킷 브레이커.** 호출 한도 오류가 규약에 있지만 한도 수치가 없어, 동시 실행 수 제한을 1차 방어로 두고 미뤘습니다 (ADR-0045)
- **변환하지 못한 원본 응답의 보관.** 지금 남기는 것은 제외 사유와 로그까지입니다
- **연동 지표·모니터링.** 로그에 원인 종류가 남게 해 두어 나중에 셀 수 있습니다 ([용어](docs/glossary.md))
- **체크인일이 지난 날짜인지 검사.** 한국 밖 숙소가 섞이면 서버 시각과 현지 날짜가 갈립니다. 어느 시간대 기준으로 볼지 정하지 못해 검사하지 않습니다
- **두 공급사가 파는 같은 숙소를 하나로 합치기.** 공통 키가 없어 숙소명으로 추정해야 하는데, 추정이 틀리면 다른 숙소가 하나로 보입니다.
  지금은 각각 내보내고 `supplier` 를 함께 싣습니다. 내부 식별자는 합치더라도 바꾸지 않기로 이미 정해 두었습니다 ([ADR-0010](docs/adr/0010-keep-internal-id-on-merge.md))

## 주요 결정

결정의 근거와 버린 대안은 각 ADR 에 있습니다. 전체 목록은 [docs/adr/README.md](docs/adr/README.md) 입니다.

| 결정 | 한 줄 이유 | |
|---|---|---|
| 서버는 Spring MVC, 공급사 호출만 WebClient | 오래 기다리는 구간이 공급사 호출뿐이라 그 부분만 논블로킹으로 둔다 | [ADR-0020](docs/adr/0020-mvc-server-with-webclient.md) |
| 검색 요청은 가상 스레드에서 기다린다 | 공급사 응답을 기다리는 동안 요청이 OS 스레드를 붙잡지 않게 한다 | [ADR-0021](docs/adr/0021-block-on-virtual-threads.md) |
| DB 에는 매핑만 저장한다 | 요금·재고의 원본은 공급사에 있고, 조회하려면 숙소 코드를 우리가 알고 있어야 한다 | [ADR-0017](docs/adr/0017-mapping-in-server-rdb.md) |
| 내부 식별자는 무작위 UUID | 순서와 개수가 드러나지 않고, 발급 방식을 바꿔도 기존 값에 영향이 없다 | [ADR-0011](docs/adr/0011-internal-id-random-uuid.md) |
| 목록에서 빠진 숙소도 행을 남기고 표시만 한다 | 지웠다가 다시 나타나면 내부 식별자가 바뀐다 | [ADR-0037](docs/adr/0037-missing-catalog-entries-kept-and-marked.md) |
| 연박 예약 가능 수는 날짜별 잔여 수의 최솟값 | 기간 전체를 팔려면 매일 밤 방이 한 실씩 있어야 한다 | [ADR-0023](docs/adr/0023-multi-night-availability-minimum.md) |
| 예약 불가 상품도 0 으로 노출한다 | 매진을 보여 줄지는 화면이 정할 일이라 서버가 정보를 버리지 않는다 | [ADR-0026](docs/adr/0026-expose-unbookable-as-zero.md) |
| 규약과 다른 공급사 응답은 세 질문으로 판정한다 | 응답을 읽을 수 있나 / 계산에 쓰이나 / 값을 하나로 정할 수 있나 | [ADR-0027](docs/adr/0027-spec-violation-handling-criteria.md) |
| 값 검증은 그 값을 담는 객체의 생성자에 둔다 | 조건과 사유가 한자리에 있고, 검증을 거치지 않은 객체가 존재할 수 없다 | [ADR-0041](docs/adr/0041-validate-in-constructors.md) |

## 문서

| 위치 | 내용 |
|---|---|
| [docs/adr/](docs/adr/) | 결정과 그 근거. 선택지와 버린 이유까지 |
| [docs/features/](docs/features/) | 기능이 무엇을 하는가, 아직 정하지 못한 질문 |
| [docs/research/](docs/research/) | 결정에 쓴 조사 기록. 출처와 조회일 |
| [docs/glossary.md](docs/glossary.md) | 용어의 뜻을 정하는 곳. 코드 이름·DB 이름과 나란히 |
| [JOURNAL.md](JOURNAL.md) | 날짜별 진행, 막힌 지점, AI 와 주고받은 반박 |
| [CLAUDE.md](CLAUDE.md) | 이 저장소에서 일하는 규칙 |

`CLAUDE.md` 를 저장소에 공개한 이유는 AI 와 함께 작성한 코드의 규칙도 결과물의 일부라고 보기 때문입니다([ADR-0003](docs/adr/0003-publish-claude-md.md)).
