---
topic: 여러 박 검색에서 객실 재고를 표현하고 계산하는 방식
checked: 2026-09-15 (핵심 인용은 원문 HTML·공식 SDK 소스에서 재대조)
---

# 연박 재고 표현 조사

여러 박을 검색할 때 날짜별 잔여 객실 수로 예약 가능 객실 수를 어떻게 정할지 판단하려고 본, 공개 개발 문서 내용이다.
판단은 이 조사를 근거로 쓰는 ADR 에 둔다.

## 요약

- 공급사가 요금·재고를 올려 보내는 방식(Booking.com, Google Hotel Prices, Hotelbeds Cache API)은 재고를 **객실 타입 × 날짜** 단위로 준다
- 판매처가 검색해 가는 방식(Expedia Rapid, Hotelbeds Booking API)은 체크인·체크아웃을 받아 **공급사가 요금마다 잔여 수 하나**를 돌려준다. 계산 방법은 공개돼 있지 않다
- Google 은 **체크아웃 전날까지 모든 날짜가 가능해야** 한다고 명시한다. 잔여 개수를 기간 전체로 어떻게 계산하는지(최솟값 등) 적은 공식 문서는 찾지 못했다
- Booking.com 은 객실 타입의 모든 유닛을 한 재고로 묶는다. 실제 객실을 날짜마다 달리 배정해도 된다고 직접 적은 문서는 찾지 못했다
- 실제 연동 데이터에는 날짜별 잔여 수 말고도 최소·최대 숙박일, 체크인·체크아웃 불가일, 판매 중지, 사전 예약 기한 같은 제약이 함께 온다

## 재고 단위와 계산 주체

| 곳 | 방식 | 원문 | 출처 |
|---|---|---|---|
| Booking.com B.XML availability | 객실 타입 × 날짜별 판매 가능 수를 올려 보냄. 종료일은 범위에서 제외 | "Specifies the number of rooms of this type that Booking.com can sell." / "The number of available rooms applies across all rates including prices set using other rates for the same room type." | [1] |
| Google Hotel Prices OTA_HotelInvCountNotifRQ | 객실 타입 × 날짜별 잔여 수. 0 은 매진, 음수는 0 으로 처리 | "The number of available rooms that can be booked for the room type. A value of zero indicates that the room type is sold out. A negative value is treated as zero." | [2] |
| Hotelbeds Cache API | 날짜별 잔여 할당(0–10, 10 은 10 이상) | "Remaining allotment from 0 to 10." / "shows the specific release/allotment per day" | [3] |
| Expedia Rapid | 체크인·체크아웃으로 조회하고 요금마다 잔여 수 하나 | "Rates returned are always available." / "The number of bookable rooms remaining with this rate in EPS inventory." | [4] |
| Hotelbeds Booking API | 요금마다 `allotment` 값 하나. 필드 정의는 문서에 없고 예시 값만 있음 | — | [5] |

## 기간 전체 예약 가능 규칙

| 곳 | 원문 | 출처 |
|---|---|---|
| Google Hotel Prices OTA_HotelAvailNotifRQ | "the room/package combination must be available for all dates of the itinerary, excluding the last day of the stay." | [6] |

## 객실 타입과 실제 객실

| 곳 | 원문 | 출처 |
|---|---|---|
| Booking.com Understanding pricing types | "when you create inventory for a room type, all units belonging to the room type are included in the inventory." | [7] |
| Mews Connector API (호텔 운영 시스템) | 예약의 객실 카테고리(`RequestedResourceCategoryId`)는 필수, 실제 객실(`AssignedResourceId`)은 선택. 카테고리는 "also known as space categories or room types" | [8] |

## 날짜별 잔여 수 말고 연박 가능 여부에 영향을 주는 데이터

| 제약 | Booking.com B.XML [1] | Google OTA_HotelAvailNotifRQ [6] | Hotelbeds Cache API [3] |
|---|---|---|---|
| 최소·최대·고정 숙박일 | `minimumstay_arrival`, `maximumstay_arrival`, `exactstay_arrival` | "SetMaxLOS, SetMinLOS, and FullPatternLOS are arrival based. SetForwardMinStay and SetForwardMaxStay are stay-through based." | 최소·최대 숙박 절이 있음(필드는 보지 않음) |
| 체크인 불가 | `closedonarrival` | Arrival 제약 | — |
| 체크아웃 불가 | `closedondeparture` | Departure 제약 | — |
| 판매 중지 | `closed` | Master 제약 | Stop sales 절 |
| 사전 예약 기한 | `min_advance_res`, `max_advance_res` | Advance booking restrictions | "Release days. 0 is day of departure" |

Booking.com 은 제약이 겹치면 의도치 않게 판매가 막힐 수 있다고 경고한다: "You can inadvertently make a room unavailable by setting multiple, overlapping restrictions." [7]

## 확인하지 못한 것

- Expedia Lodging Supply AR API(공급사가 올려 보내는 쪽): 문서 페이지가 스크립트로만 그려져 필드 정의를 보지 못했다
- OpenTravel Alliance 원 명세와 HTNG: 열어보지 못했다. 위 Google 문서는 OTA 메시지를 Google 이 구현한 문서다
- Hotelbeds Booking API 가 기간 전체가 가능한 객실만 돌려주는지, `allotment` 가 기간 중 최솟값인지
- 실제 객실을 체크인 때 배정한다고 직접 적은 공식 문서
- SiteMinder, 국내 연동 플랫폼의 연박 재고 설명

## 이 저장소의 공급사와 비교

이 저장소의 두 공급사는 검색해 가는 방식의 API 이면서, 응답은 올려 보내는 방식처럼 **객실 타입 × 날짜별 잔여 수**를 준다.
기간 전체의 잔여 수 하나를 공급사가 계산해 주지 않고, 위 표의 숙박일 수·체크인 불가 같은 제약도 주지 않는다.
공급사는 요청 인원을 수용할 수 있는 객실 타입만 돌려준다.

## 출처

| # | 출처 | 해당 문장·절로 이동 |
|---|---|---|
| 1 | Booking.com Connectivity, B.XML availability | [roomstosell](https://developers.booking.com/connectivity/docs/b_xml-availability#:~:text=Specifies%20the%20number%20of%20rooms%20of%20this%20type%20that%20Booking.com%20can%20sell) · [across all rates](https://developers.booking.com/connectivity/docs/b_xml-availability#:~:text=The%20number%20of%20available%20rooms%20applies%20across%20all%20rates) |
| 2 | Google Hotel Prices, OTA_HotelInvCountNotifRQ | [@Count](https://developers.google.com/hotels/hotel-prices/xml-reference/ari-inv#:~:text=A%20value%20of%20zero%20indicates%20that%20the%20room%20type%20is%20sold%20out) |
| 3 | Hotelbeds Cache API, internal inventory process | [Allotment](https://developer.hotelbeds.com/documentation/hotels/cache-api/internal-inventory-process/#:~:text=Remaining%20allotment%20from%200%20to%2010) · [Release](https://developer.hotelbeds.com/documentation/hotels/cache-api/internal-inventory-process/#:~:text=Release%20days.%200%20is%20day%20of%20departure) |
| 4 | Expedia Rapid Java SDK (API 스펙에서 생성) | [Rate.kt](https://github.com/ExpediaGroup/rapid-java-sdk/blob/main/code/src/main/kotlin/com/expediagroup/sdk/rapid/models/Rate.kt) 의 `available_rooms` 주석. `main` 브랜치라 줄 번호는 밀린다 |
| 5 | Hotelbeds Booking API, workflow | [Searching for a hotel rate](https://developer.hotelbeds.com/documentation/hotels/booking-api/workflow/#:~:text=Searching%20for%20a%20hotel%20rate) |
| 6 | Google Hotel Prices, OTA_HotelAvailNotifRQ | [모든 날짜가 가능해야 함](https://developers.google.com/hotels/hotel-prices/xml-reference/ari-avail#:~:text=must%20be%20available%20for%20all%20dates%20of%20the%20itinerary) · [숙박일 수 제약](https://developers.google.com/hotels/hotel-prices/xml-reference/ari-avail#:~:text=SetForwardMaxStay%20are%20stay%2Dthrough%20based) |
| 7 | Booking.com Connectivity, Understanding pricing types | [객실 타입의 모든 유닛](https://developers.booking.com/connectivity/docs/understanding-pricing-types#:~:text=all%20units%20belonging%20to%20the%20room%20type%20are%20included%20in%20the%20inventory) · [겹치는 제약](https://developers.booking.com/connectivity/docs/understanding-pricing-types#:~:text=You%20can%20inadvertently%20make%20a%20room%20unavailable) |
| 8 | Mews Connector API, Resource categories · Reservations | [room types](https://docs.mews.com/connector-api/operations/resourcecategories#:~:text=also%20known%20as%20space%20categories%20or%20room%20types) · [AssignedResourceId](https://docs.mews.com/connector-api/operations/reservations#:~:text=Identifier%20of%20the%20assigned%20Resource) |
