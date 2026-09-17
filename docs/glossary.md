# 용어

이 저장소에서 쓰는 말의 뜻을 여기서만 정한다. 다른 문서와 코드는 쓰기만 하고 다시 정의하지 않는다.
한쪽이 바뀌면 다른 쪽이 틀린 말이 되기 때문이다.

말과 코드 이름과 DB 이름을 한 줄에 둔다. 대화에서는 왼쪽 말을, 코드에서는 가운데 이름을 쓴다.

## 도메인

| 말 | 코드·DB 이름 | 뜻과 헷갈리기 쉬운 것 |
|---|---|---|
| **공급사** | `supplier` | 우리가 상품을 받아 파는 외부 숙박 사업자. 지금은 A·B 둘 |
| **숙소** | `hotel_mapping`, `FetchedHotel` | 호텔·게스트하우스 같은 건물 하나. 공급사 안에서 코드로 유일하다 |
| **객실 타입** | `room_type_mapping`, `FetchedRoomType` | "디럭스 더블" 처럼 파는 단위. **물리 객실 하나가 아니다.** 그 숙소 안에서만 코드가 유일하다.<br>공급사 B 는 이것을 `roomId`·`roomName` 이라 부르지만 이름과 달리 객실 타입이다 |
| **최대 수용 인원** | `max_occupancy` | 객실 1실에 묵을 수 있는 인원. 성인과 아동을 합한 수다 |
| **예약 가능 객실 수** | (검색 응답) | 그 객실 타입을 몇 실 팔 수 있는가. 연박이면 날짜별 잔여 수의 최솟값 ([ADR-0023](adr/0023-multi-night-availability-minimum.md)).<br>0 이면 예약 불가다. 응답에서 빼지 않고 0 으로 내보낸다 ([ADR-0026](adr/0026-expose-unbookable-as-zero.md)) |
| **숙박 구간** | `StayPeriod` | 체크인일과 체크아웃일 한 쌍. **체크아웃일은 숙박에 넣지 않는다** ([ADR-0043](adr/0043-stay-period-value-object.md)).<br>3박이면 날짜는 셋이고 체크아웃일은 그중에 없다 |
| **인원** | `GuestCount` | 검색에 넣는 성인과 아동의 **수**. 나이는 받지 않는다. 성인 최소 인원을 두지 않고, 합이 1 이상이면 된다 ([ADR-0048](adr/0048-guest-count-invariants.md)).<br>객실에 맞는지는 공급사가 최대 수용 인원으로 거른다 |
| **금액** | `Money` | 액수와 통화를 함께 담는 값 ([ADR-0042](adr/0042-rate-as-tax-included-total.md)). 통화 없는 액수는 존재하지 않는다.<br>0 은 된다. 음수만 막는다 ([ADR-0027](adr/0027-spec-violation-handling-criteria.md) 의 요금 표) |
| **요금** | `Rate` | 금액과 판매 조건을 함께 가진, **팔리는 단위**. 금액 자체가 아니다.<br>같은 객실 타입이라도 조건이 다르면 다른 요금이다 |
| **판매 조건** | `RateConditions` | 그 요금으로 살 때 따라오는 것. 지금 담는 것은 조식 포함 여부 하나 ([ADR-0044](adr/0044-rate-conditions-as-value-object.md)).<br>취소 조건은 공급사가 주지 않는다 |

## 공급사와 만나는 곳

| 말 | 코드 이름 | 뜻과 헷갈리기 쉬운 것 |
|---|---|---|
| **숙소 목록 API** | `CatalogAdapter.fetchCatalog` | 공급사가 취급하는 숙소와 객실 타입을 조건 없이 전부 주는 API. 드물게 바뀐다. 요금·재고는 여기 없다 |
| **재고·요금 API** | `AvailabilityAdapter.fetchAvailability` | 숙소 코드 목록과 날짜·인원을 주면 그 조건의 재고와 요금을 주는 API. 매번 바뀐다.<br>한 번에 숙소 코드 50개까지 받고 **넘으면 공급사가 오류로 거절한다** ([ADR-0045](adr/0045-search-concurrency-and-budget.md)) |
| **chunk** | `AvailabilityRequest` | 위 상한에 맞춰 숙소 코드를 50개 이하로 나눈 한 덩어리에 숙박 구간·인원을 붙인 것. 검색 한 건이 chunk 여러 개를 호출한다.<br>50개를 넘는 chunk 는 만들어지지 않는다 ([ADR-0047](adr/0047-request-types-in-supplier-package.md)) |
| **공급사 인터페이스** | `SupplierAdapter` | 한 공급사가 구현해야 하는 두 인터페이스(`CatalogAdapter`·`AvailabilityAdapter`)를 묶은 것. consumer 는 이것을 보지 않는다.<br>한 인터페이스를 빠뜨리면 컴파일에서 드러나게 하는 것이 유일한 역할이다 ([ADR-0031](adr/0031-supplier-adapter-boundaries.md)) |
| **어댑터** | `SupplierAAdapter` 등 | 한 공급사의 API 를 호출하고 그 응답을 우리 형태로 바꾸는 클래스. 공급사마다 하나 ([ADR-0031](adr/0031-supplier-adapter-boundaries.md)).<br>검증은 하지 않는다. 공급사마다 다른 것만 흡수한다 |
| **consumer** | `CatalogSyncService` 등 | 어댑터를 주입받아 쓰는 쪽. 목록 동기화와 검색 둘 |
| **공급사 실패** | `SupplierResponseException` | 응답을 스펙대로 받지 못한 것. HTTP 상태, 본문의 결과 코드, 본문을 읽지 못한 것,<br>타임아웃, 연결하지 못한 것이 모두 여기 든다 ([ADR-0027](adr/0027-spec-violation-handling-criteria.md)).<br>공급사가 "실패"라고 말한 것만이 아니다 |
| **동일 숙소** | `same_hotel_id`, `sameHotelId` | **사람이 확인한** 같은 숙소의 모음. 확인된 짝끼리 같은 값을 갖고, 짝이 없는 숙소도 자기 값을 갖는다 ([ADR-0058](adr/0058-same-hotel-confirmed-pairs-only.md)).<br>이번 응답 안에서 모으는 데 쓰는 값이다. 짝이 바뀌면 값이 바뀌니 저장해 다시 찾는 키로 쓰지 않는다. 숙소를 가리키는 값은 내부 숙소 식별자다 |
| **동일 숙소 후보** | (동기화가 계산) | 정규화한 숙소명이 같은 다른 공급사 숙소 쌍. **추정이라 응답에 내지 않는다.** 사람이 확인하면 동일 숙소가 된다 |
| **서킷** | `SupplierCircuitBreakers` | 공급사마다 하나. 재시도까지 거친 chunk 의 최종 결과를 세어, 실패가 많으면 **열어서** 한동안 그 공급사를 호출하지 않는다 ([ADR-0056](adr/0056-circuit-breaker-per-supplier-outside-retry.md)).<br>검색에만 있고 목록 동기화에는 없다. 공급사 실패만 센다 |
| **일시적인 실패** | `SupplierResponseException.transient` | **재시도 가능한(transient)** 실패. 5xx·429·연결 실패가 여기 든다 ([ADR-0051](adr/0051-retry-transient-supplier-failures.md)).<br>타임아웃은 아니다. 느리다는 신호라 재시도해도 느리다.<br>**"재시도한다"는 뜻이 아니다.** 실제로 재시도할지는 호출하는 쪽이 정한다. 검색은 재시도하고 목록 동기화는 재시도하지 않는다 |
| **부분 실패** | `SupplierResult.status = FAILED` | 공급사 하나가 실패해도 나머지 공급사 결과로 응답하는 것. 응답에 그 사실을 드러낸다.<br>**실패는 그 공급사 응답을 아예 만들 수 없었다는 뜻**이다. 매핑을 못 읽었거나, 모든 chunk 가 실패했거나, 검색 전체 타임아웃을 넘겼다 ([ADR-0050](adr/0050-partial-chunk-failure.md)) |
| **실패한 chunk** | `SupplierResult.failedChunks` | 성공한 공급사 안에서 호출하지 못한 chunk 수. 그 chunk 의 숙소는 응답에 없다. 상태는 성공이다 ([ADR-0050](adr/0050-partial-chunk-failure.md)) |

## 우리 안에서 쓰는 형태

요구사항이 말하는 **"표준 모델"** 은 우리 코드에서 셋으로 나뉜다. 그 말만 쓰지 말고 어느 것인지 밝힌다.

| 말 | 코드 이름 | 뜻 |
|---|---|---|
| **받은 값** | `Fetched…` | 어댑터가 공급사 응답에서 꺼내 놓은 값. **아직 검증하지 않았다.** 값이 없을 수 있어 전부 null 을 허용한다.<br>어느 공급사 것인지는 들어 있지 않다. 어댑터가 안다 ([ADR-0049](adr/0049-supplier-id-on-adapter-only.md)).<br>요금은 공급사가 준 형식 그대로다. 날짜별(`FetchedPricing.Daily`)이거나 총액(`FetchedPricing.Total`)이고, 하나의 총액으로 만드는 것은 정규화가 한다 |
| **정규화한 값** | `Normalized…` | 정규화를 마치고 매핑에 넣을 값. 조건을 어긴 것은 여기까지 오지 않는다 |
| **검색 결과** | `SearchResult`, `SupplierResult` | 정규화를 마치고 공급사별 상태·건수와 함께 합친 것. **내부 형태다** |
| **검색 응답** | `SearchResponse` | 위를 응답 계약 형식으로 다시 담은 것. 내부 형태를 그대로 내보내지 않아, 내부 타입이 바뀌어도 계약이 따라 바뀌지 않는다 |

| 말 | 코드 이름 | 뜻과 헷갈리기 쉬운 것 |
|---|---|---|
| **정규화** | `CatalogNormalizer`, `AvailabilityNormalizer` | 받은 값에서 쓸 것과 제외할 것을 가르는 일. 목록 쪽과 재고·요금 쪽이 하나씩 있다.<br>값이 유효한지는 값을 담는 객체가 검증한다 ([ADR-0041](adr/0041-validate-in-constructors.md)). 정규화는 만들어 보고 못 만든 것을 모은다 |
| **제외 항목** | `ExcludedItem`, `ExcludedRoomType` | 값을 정할 수 없어 결과에서 뺀 숙소나 객실 타입. **사유를 함께 남긴다**.<br>재고·요금 쪽은 **매핑에 없어 뺀 것**(`UNMAPPED`)과 **스펙과 달라 뺀 것**(`OUT_OF_SPEC`)을 나눈다. 응답에 세어 싣는 것은 뒤쪽뿐이다 ([ADR-0046](adr/0046-supplier-status-in-search-response.md)) |
| **제외 사유** | `….reason` | 왜 뺐는지. 값을 담는 객체가 거부하며 낸 문장이 그대로 들어간다 |
| **검색 결과 항목** | `AvailableRoomType` | 정규화를 마친, 한 공급사의 숙소 하나의 객실 타입 하나. 내부 식별자·이름·최대 수용 인원·예약 가능 객실 수·요금을 가진다 |
| **격리** | `quarantine_record`, `QuarantineRecorder` | **요구사항의 선택 항목 이름**이다. 변환하지 못한 응답의 **원본을 버리지 않고 따로 보관**하는 것을 말한다.<br>우리는 **같은 문제를 한 행으로 그룹화해** 횟수·처음과 마지막 시각·마지막 사유·마지막 원본을 남긴다 ([ADR-0055](adr/0055-quarantine-grouped-in-db.md)). 원본은 공급사 원문이 아니라 우리가 읽어 들인 항목이다.<br>**우리가 항목을 빼는 일 자체를 "격리"라고 부르지 않는다.** 뺀 것을 남기는 기록이 격리다 |

## 매핑과 DB

DB 에 저장하는 것은 매핑뿐이다 ([ADR-0017](adr/0017-mapping-in-server-rdb.md)). 요금·재고는 저장하지 않는다.

| 말 | DB 이름 | 뜻과 헷갈리기 쉬운 것 |
|---|---|---|
| **매핑** | `hotel_mapping`, `room_type_mapping` | 공급사 코드와 내부 식별자의 짝 |
| **내부 식별자** | `internal_hotel_id`, `internal_room_type_id` | 우리가 발급해 쓰는 식별자. 무작위 UUID ([ADR-0011](adr/0011-internal-id-random-uuid.md)).<br>공급사 코드가 아니다. 같은 공급사 상품은 다시 조회해도 같은 값이다 |
| **공급사 코드** | `supplier_hotel_code`, `room_type_code` | 공급사가 그 숙소·객실 타입에 붙인 코드. 숙소 코드는 공급사 안에서, 객실 타입 코드는 그 숙소 안에서 유일하다 |
| **목록 동기화** | `CatalogSyncService` | 숙소 목록 API 를 받아 매핑에 반영하는 일. 기동 직후 한 번, 그 뒤 주기마다 ([ADR-0013](adr/0013-catalog-sync-on-startup-and-interval.md)).<br>고객 검색이 이것을 호출하지 않는다 ([ADR-0014](adr/0014-detect-drift-in-search-correct-in-sync.md)) |
| **`missing_since`** | `missing_since` | **이번 목록에 없었던 시각.** 값이 비어 있으면 목록에 있다는 뜻이다 ([ADR-0037](adr/0037-missing-catalog-entries-kept-and-marked.md)).<br>지웠다는 뜻이 아니다. 행은 남고, 다시 나타나면 같은 내부 식별자로 값이 비워진다.<br>말할 때도 컬럼 이름 그대로 부른다. "사라짐" 같은 말은 삭제로 읽히기 쉽다 |

### 테이블

```
hotel_mapping
  internal_hotel_id    uuid   PK      내부 숙소 식별자
  supplier             text           공급사
  supplier_hotel_code  text           공급사가 붙인 숙소 코드
  hotel_name           text           숙소명
  missing_since        timestamptz    이번 목록에 없었던 시각 (비어 있으면 목록에 있음)
  UNIQUE (supplier, supplier_hotel_code)

room_type_mapping
  internal_room_type_id uuid  PK      내부 객실 타입 식별자
  internal_hotel_id     uuid  FK      어느 숙소의 객실 타입인가
  room_type_code        text          공급사가 붙인 객실 타입 코드
  room_type_name        text          객실 타입명
  max_occupancy         int           객실 1실의 최대 수용 인원
  missing_since         timestamptz   위와 같다
  UNIQUE (internal_hotel_id, room_type_code)
```
