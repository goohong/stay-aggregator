---
topic: 검색 API 명세
checked: 2026-09-17
---

# 검색 API

바깥으로 열린 API 는 검색 하나다. **이 문서가 그 계약이다.**

말의 뜻은 [용어집](glossary.md) 에, 왜 이런 모양인지는 [ADR](adr/) 에, 이 API 가 코드의 어디를 지나는지는
[아키텍처 문서](architecture.md#3-검색-한-건이-지나는-길) 에 있다. 여기서는 주고받는 것만 적는다.

계약의 출처는 넷이다. 이 문서와 어긋나면 아래가 맞다.

| 무엇 | 어디 |
|---|---|
| 응답 구조 | `app/src/main/kotlin/com/stayaggregator/search/SearchResponse.kt` |
| 요청 파라미터 | `app/src/main/kotlin/com/stayaggregator/search/SearchController.kt` |
| 입력 조건과 거부 사유 | `app/src/main/kotlin/com/stayaggregator/domain/` 의 `StayPeriod`, `GuestCount` |
| 오류 응답 | `app/src/main/kotlin/com/stayaggregator/search/SearchErrorHandler.kt` |

계약을 지키는 테스트는 `app/src/test/kotlin/com/stayaggregator/search/SearchControllerTest.kt` 다.
아래 표의 "확인" 칸에 그 테스트 이름을 적었다.

## 1. 엔드포인트

```
GET /api/v1/stays/search
```

인증은 없다. 응답은 `application/json` 이다.

## 2. 요청

모두 쿼리 파라미터이고 **넷 다 필수다.** 하나라도 빠지면 400 이다.

| 이름 | 타입 | 필수 | 규칙 | 확인 |
|---|---|---|---|---|
| `checkIn` | 날짜 (`yyyy-MM-dd`) | 예 | 체크인일 | `SearchControllerTest.날짜 형식이 틀리면 400 이다` |
| `checkOut` | 날짜 (`yyyy-MM-dd`) | 예 | **체크인일보다 뒤여야 한다.** 같은 날도 안 된다 | `StayPeriodTest`, `SearchControllerTest.체크아웃이 체크인보다 뒤가 아니면 400 이고 사유는 값 객체의 문장이다` |
| `adults` | 정수 | 예 | 0 이상 | `GuestCountTest` |
| `children` | 정수 | 예 | 0 이상 | `GuestCountTest` |

두 인원의 **합이 1 이상**이어야 한다. 성인 최소 인원은 두지 않아 아동만으로도 검색된다
([ADR-0048](adr/0048-guest-count-invariants.md))
→ `SearchControllerTest.서비스에 넘기는 값은 요청 파라미터로 만든 값 객체다`, `SearchControllerTest.인원이 0명이면 400 이다`

알아 둘 것 둘.

- **체크아웃일은 숙박에 넣지 않는다.** `checkIn=2026-10-05&checkOut=2026-10-08` 은 3박이고, 재고와 요금은 10/05·10/06·10/07 세 밤의 것이다 ([ADR-0043](adr/0043-stay-period-value-object.md))
- **체크인일이 지난 날짜인지 검사하지 않는다.** 숙소의 시간대를 모르기 때문이다 ([ADR-0052](adr/0052-no-past-date-check.md))

```bash
curl 'localhost:8080/api/v1/stays/search?checkIn=2026-10-05&checkOut=2026-10-08&adults=2&children=0'
```

## 3. 응답 (200)

### 최상위

| 필드 | 타입 | null | 뜻 |
|---|---|---|---|
| `checkIn` | string (`yyyy-MM-dd`) | 아니오 | 요청에 받은 값 그대로 |
| `checkOut` | string (`yyyy-MM-dd`) | 아니오 | 요청에 받은 값 그대로 |
| `nights` | int | 아니오 | 박 수. 1 이상이고, 체크아웃일은 세지 않는다 |
| `adults` | int | 아니오 | 요청에 받은 값 그대로 |
| `children` | int | 아니오 | 요청에 받은 값 그대로 |
| `suppliers` | array | 아니오 | 호출 대상 공급사마다 한 건. **공급사 식별자 오름차순** |
| `roomTypes` | array | 아니오 | 검색 결과 항목. 결과가 없으면 빈 배열 |

`suppliers` 의 순서는 `SearchService.search` 의 `sortedBy { it.supplierId }` 가 정한다. 주입 순서에 기대지 않는다.

`roomTypes` 는 공급사 순서로 이어 붙인다. **한 공급사 안의 항목 순서는 정해 두지 않았다.**
chunk 호출이 병렬이라 돌아오는 순서가 매번 같지 않다 → `search/SearchService.kt` 의 `fetchAll`, `search/SearchResponse.kt` 의 `from`

### `suppliers[]` — 공급사마다 결과를 빠짐없이 만들었는가

| 필드 | 타입 | null | 뜻 |
|---|---|---|---|
| `supplier` | string | 아니오 | 공급사 식별자. 지금은 `"a"`, `"b"` 둘이다 |
| `status` | string | 아니오 | `SUCCEEDED` 또는 `FAILED` |
| `failureReason` | string | **예** | `FAILED` 일 때만 값이 있다. 성공한 공급사는 `null` 이다 |
| `roomTypeCount` | int | 아니오 | 이 공급사가 `roomTypes` 에 내보낸 항목 수. `FAILED` 면 0 |
| `outOfSpecCount` | int | 아니오 | 응답이 스펙과 달라 뺀 객실 타입 수 |
| `failedChunks` | int | 아니오 | 호출하지 못한 chunk 수 |

읽는 법은 6절에 있다.

### `roomTypes[]` — 검색 결과 항목

항목 하나는 **한 공급사의, 숙소 하나의, 객실 타입 하나**다. 같은 숙소를 두 공급사가 팔면 항목이 둘로 나간다
([ADR-0058](adr/0058-same-hotel-confirmed-pairs-only.md)).

| 필드 | 타입 | null | 뜻 |
|---|---|---|---|
| `hotelId` | string (UUID) | 아니오 | 내부 숙소 식별자. 공급사 코드가 아니다 ([ADR-0011](adr/0011-internal-id-random-uuid.md)) |
| `hotelName` | string | 아니오 | 숙소명. **숙소 목록에서 받아 저장해 둔 값**이다 ([ADR-0012](adr/0012-static-info-from-catalog.md)) |
| `roomTypeId` | string (UUID) | 아니오 | 내부 객실 타입 식별자 |
| `roomTypeName` | string | 아니오 | 객실 타입명. 위와 같은 저장값이다 |
| `maxOccupancy` | int | 아니오 | 객실 **1실**의 최대 수용 인원. 성인과 아동을 합한 수다 |
| `supplier` | string | 아니오 | 이 항목을 판 공급사 |
| `availableRooms` | int | 아니오 | 요청 기간 **전체**에 예약할 수 있는 객실 수. 연박이면 날짜별 잔여 수의 최솟값이다 ([ADR-0023](adr/0023-multi-night-availability-minimum.md)) |
| `rate` | object | 아니오 | 아래 표 |

`availableRooms` 는 **0 일 수 있고, 0 이어도 응답에서 빼지 않는다.** 매진을 보여 줄지는 화면이 정할 일이다
([ADR-0026](adr/0026-expose-unbookable-as-zero.md))
→ `SearchControllerTest.예약 불가 상품도 예약 가능 객실 수 0 으로 응답에 나간다`

### `roomTypes[].rate`

| 필드 | 타입 | null | 뜻 |
|---|---|---|---|
| `totalAmount` | int64 | 아니오 | **세금을 포함한 기간 전체 총액.** 통화의 최소 단위 정수이고 0 이상이다 |
| `currency` | string | 아니오 | ISO 4217 세 글자 |
| `breakfastIncluded` | boolean | 아니오 | 조식 포함 여부 |

받는 쪽이 알아야 할 것 셋.

- **1박 얼마는 없다.** 총액과 `nights` 로 받는 쪽이 계산한다. 공급사 하나가 총액만 주기 때문에 날짜별로는 되돌릴 수 없다 ([ADR-0042](adr/0042-rate-as-tax-included-total.md))
- **세금 내역도 없다.** 포함 여부만 다르게 주는 공급사가 있어 같은 문제가 세금 쪽에서 되풀이된다 (ADR-0042)
- **통화를 환산하지 않는다.** 통화가 다른 항목이 한 응답에 섞여 나가므로 `currency` 를 항목마다 봐야 한다 ([ADR-0053](adr/0053-mixed-currency-exposure.md))

## 4. 예시

### 정상 (두 공급사 모두 성공)

아래 값은 Mock 의 고정 응답(`mock-supplier/src/main/kotlin/com/stayaggregator/mocksupplier/SupplierFixtures.kt`)을
이 문서의 규칙대로 계산한 것이다. `hotelId`·`roomTypeId` 는 목록 동기화 때 발급하는 무작위 UUID 라 실행마다 다르다.

```json
{
  "checkIn": "2026-10-05",
  "checkOut": "2026-10-08",
  "nights": 3,
  "adults": 2,
  "children": 0,
  "suppliers": [
    { "supplier": "a", "status": "SUCCEEDED", "failureReason": null,
      "roomTypeCount": 2, "outOfSpecCount": 0, "failedChunks": 0 },
    { "supplier": "b", "status": "SUCCEEDED", "failureReason": null,
      "roomTypeCount": 1, "outOfSpecCount": 0, "failedChunks": 0 }
  ],
  "roomTypes": [
    {
      "hotelId": "20ac7cce-2b3f-4a54-9f3e-0f1f6f2f8a11",
      "hotelName": "Hangang View Hotel",
      "roomTypeId": "6a586d72-6b44-4a3b-8a2a-2f1c9a7e4d02",
      "roomTypeName": "Deluxe Double",
      "maxOccupancy": 2,
      "supplier": "a",
      "availableRooms": 2,
      "rate": { "totalAmount": 396000, "currency": "KRW", "breakfastIncluded": false }
    },
    {
      "hotelId": "3f9c1d84-7e12-4c0b-9a55-4d2b8e6c1b73",
      "hotelName": "Bukchon Hanok Stay",
      "roomTypeId": "b1e4a907-55c8-4c2d-9f77-6a0c3d5e2f14",
      "roomTypeName": "Standard Twin",
      "maxOccupancy": 2,
      "supplier": "a",
      "availableRooms": 0,
      "rate": { "totalAmount": 280500, "currency": "KRW", "breakfastIncluded": false }
    },
    {
      "hotelId": "c4d7f2a1-91b6-4e08-8c33-7b5e0a9d6c25",
      "hotelName": "Hangang View Hotel",
      "roomTypeId": "e8b30f65-1d4a-4f9c-bb21-3c6a7e2d5081",
      "roomTypeName": "Deluxe Double Room",
      "maxOccupancy": 2,
      "supplier": "b",
      "availableRooms": 2,
      "rate": { "totalAmount": 431000, "currency": "KRW", "breakfastIncluded": true }
    }
  ]
}
```

읽는 법 둘.

- 같은 `Hangang View Hotel` 이 `supplier` 만 다르게 두 번 나온다. 사람이 확인한 짝만 합치기로 했고 아직 합치지 않는다 (ADR-0058)
- `Bukchon Hanok Stay` 는 둘째 밤 잔여가 0 이라 `availableRooms` 가 0 이다. 요금은 그대로 실린다 (ADR-0026)

### 부분 실패 (공급사 하나가 실패)

**공급사가 실패해도 HTTP 상태는 200 이다.** 실패는 `suppliers` 안에서 드러난다 ([ADR-0046](adr/0046-supplier-status-in-search-response.md)).

```jsonc
{
  "checkIn": "2026-10-05",
  "checkOut": "2026-10-08",
  "nights": 3,
  "adults": 2,
  "children": 0,
  "suppliers": [
    { "supplier": "a", "status": "SUCCEEDED", "failureReason": null,
      "roomTypeCount": 2, "outOfSpecCount": 0, "failedChunks": 0 },
    { "supplier": "b", "status": "FAILED",
      "failureReason": "공급사 B 가 실패를 알렸다: resultCode=E503, resultMessage=TEMPORARILY_UNAVAILABLE",
      "roomTypeCount": 0, "outOfSpecCount": 0, "failedChunks": 1 }
  ],

  // 실패한 공급사의 항목은 없다. 남는 것은 공급사 a 의 두 건이고 모양은 위 예시와 같다
  "roomTypes": [
    { "supplier": "a", "…": "…" },
    { "supplier": "a", "…": "…" }
  ]
}
```

실패 문장은 실패 종류마다 다르다. 공급사가 알린 것(위), 검색 전체 타임아웃을 넘긴 것,
매핑을 읽지 못한 것이 각각 다른 문장으로 실린다
→ `SearchServiceIntegrationTest.한 공급사가 실패해도 나머지 결과로 응답하고 실패한 공급사가 사유와 함께 드러난다`

직접 만들어 보려면 Mock 의 응답 모드를 바꾼다. 명령은 [README 의 "공급사 장애를 만들어 보기"](../README.md#공급사-장애를-만들어-보기) 에 있다.

## 5. 오류 응답

### 400 — 입력이 조건을 어겼다

```json
{ "message": "체크아웃일이 체크인일보다 뒤가 아니다" }
```

**사유 문장은 값을 담는 객체의 생성자가 거부하며 낸 것을 그대로 싣는다.** 오류 처리기가 사유를 다시 쓰지 않는다.
조건과 사유가 두 곳으로 갈라지지 않게 하려는 것이다 ([ADR-0041](adr/0041-validate-in-constructors.md))
→ `search/SearchErrorHandler.kt`

| 어긴 조건 | `message` | 문장이 있는 곳 |
|---|---|---|
| 체크아웃일이 체크인일보다 뒤가 아니다 | `체크아웃일이 체크인일보다 뒤가 아니다` | `domain/StayPeriod.kt` |
| 성인 인원이 음수 | `성인 인원이 음수다` | `domain/GuestCount.kt` |
| 아동 인원이 음수 | `아동 인원이 음수다` | `domain/GuestCount.kt` |
| 인원의 합이 0 | `인원이 0명이다` | `domain/GuestCount.kt` |

확인 → `SearchControllerTest.체크아웃이 체크인보다 뒤가 아니면 400 이고 사유는 값 객체의 문장이다`,
`SearchControllerTest.인원이 0명이면 400 이다`

**조건이 늘면 이 표도 늘어난다.** 값 객체에 검사를 더하면 여기에 줄을 더한다.

### 400 — 형식이 틀렸거나 파라미터가 빠졌다

날짜가 `yyyy-MM-dd` 가 아니거나 숫자가 아닌 인원, 빠진 파라미터는 우리 오류 처리기까지 오지 않고 Spring 이 먼저 거절한다. 그것도 400 이다.
**본문 형식은 이 문서가 정하지 않았다.** 테스트도 상태 코드만 본다
→ `SearchControllerTest.날짜 형식이 틀리면 400 이다`, `SearchControllerTest.파라미터가 빠지면 400 이다`

### 공급사 실패는 오류 응답이 아니다

공급사가 하나도 남김없이 실패해도 200 이고, `suppliers` 가 모두 `FAILED` 인 응답이 나간다.
받는 쪽이 "요청이 틀렸다"와 "공급사가 답하지 않았다"를 상태 코드 하나로 구분하지 않게 한 것이다 (ADR-0046, [ADR-0050](adr/0050-partial-chunk-failure.md)).

## 6. 공급사별 상태를 읽는 법

`suppliers` 는 **이 응답이 빠짐없는지**를 말한다. 항목이 몇 건이냐가 아니라, 못 본 것이 있느냐다.

| 필드 | 무엇을 뜻하나 | 0 이 아니면 |
|---|---|---|
| `status = FAILED` | **그 공급사 결과를 아예 만들지 못했다.** 매핑을 읽지 못했거나, 모든 chunk 가 실패했거나, 검색 전체 타임아웃을 넘겼다 (ADR-0050) | 그 공급사 상품은 이 응답에 하나도 없다 |
| `failureReason` | 왜 실패했는지. **사람이 읽는 문장이고 형식을 정해 두지 않았다.** 코드로 분기하지 말 것 | — |
| `roomTypeCount` | 이 공급사가 `roomTypes` 에 내보낸 항목 수 | — |
| `outOfSpecCount` | 응답이 스펙과 달라 뺀 객실 타입 수 ([ADR-0027](adr/0027-spec-violation-handling-criteria.md)) | 그 공급사 결과가 그만큼 줄었다. 매핑에 없어 뺀 것은 여기 세지 않는다 (ADR-0046) |
| `failedChunks` | 호출하지 못한 chunk 수. 상태는 성공일 수 있다 | 그 공급사의 일부 숙소는 이 응답에 없다 |

`status = SUCCEEDED` 이면서 `failedChunks` 가 0 이 아닌 조합이 정상이다.
"성공했고, 다만 N개 chunk 는 물어보지 못했다"를 뜻한다 (ADR-0050)
→ `SearchServiceIntegrationTest.chunk 하나가 실패해도 다른 chunk 의 항목은 나가고 실패한 chunk 수가 실린다`

`outOfSpecCount` 로 센 항목은 버리지 않고 격리 기록에 남는다. 무엇이 문제였는지는 그 기록에 있다
([ADR-0055](adr/0055-quarantine-grouped-in-db.md))

## 7. 계약이 바뀔 때

고칠 곳은 둘이다. **응답 DTO(`search/SearchResponse.kt`)와 이 문서를 같은 커밋에 담는다.**

내부 결과 타입(`SearchResult`, `SupplierResult`)이 바뀌어도 이 문서는 바뀌지 않는다. 그러라고 응답 DTO 를 따로 둔다
→ `search/SearchResponse.kt` 의 클래스 주석

## 8. springdoc 을 넣기 전까지

API 문서를 springdoc-openapi 로 코드에서 만들기로 정했고 아직 넣지 않았다 ([ADR-0063](adr/0063-springdoc-openapi.md)).
**그때까지 계약은 이 문서다.**

넣으면 필드 단위 계약은 생성된 문서(`/v3/api-docs`)가 맡고, 이 문서에는 예시와 "읽는 법"이 남는다.
ADR-0063 이 README 에 대해 정한 분담과 같은 기준이다.
