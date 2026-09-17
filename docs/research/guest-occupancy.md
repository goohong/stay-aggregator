---
topic: 검색 인원(성인·아동)의 처리
checked: 2026-09-17
---

# 검색 인원(성인·아동) 조사

검색 API 가 받는 인원에 어떤 불변식을 둘지 정하려고 조사했다.
처음에는 "성인 없이 아동만 묵는 예약은 성립하지 않는다"를 전제로 삼으려 했는데 그 전제가 맞는지 확인한 것이다.
여기에는 확인한 사실만 적고 판단은 해당 ADR 에 적는다.

**한국 밖 숙소도 다룰 수 있다는 것을 전제로 조사했다.** 그래서 국내 법령만 보지 않고 글로벌 연동 규격을 함께 봤다.

## 확인 수준 표기

문서 페이지를 열어 읽었고, 아래 표의 **인용** 칸은 페이지에서 돌아온 문장을 그대로 옮긴 것이다.
표현이 요약을 거쳐 원문과 다를 수 있는 것은 **요지만** 으로 표시했고 문장으로 인용하지 않았다.
열지 못한 문서는 **확인 못 함** 으로 적었다.

## 요약

- **아동만 묵는 숙박이 법으로 금지된 것은 아니다.** 한국은 보호자 동의서가 있으면 가능하고,
  금지되는 것은 혼숙이다. 거절은 업소 재량이다
- **그런데 글로벌 연동 규격은 성인 최소 1명을 요구한다.** Booking.com 은 문서에 명문으로 적었다
- **아동의 나이 경계는 숙소가 정한다.** 국제 규격 둘 다 아동 **나이**를 요청에 실으라고 하고, 요금이 나이 구간에 따라 달라진다
- 미국 숙소는 최소 투숙 연령을 두는 곳이 있다. 이유로 계약 강제 불가와 보호 의무가 설명된다 (2차 출처)

## 1. 한국 법령

| 사실 | 인용 | 원문 |
|---|---|---|
| 청소년 단독 숙박 자체가 금지는 아니다. 보호자 동의서가 있으면 가능하다 | "청소년도 '보호자의 숙박동의서'가 있으면 숙박이 가능합니다." | [대한민국 정책브리핑](https://www.korea.kr/news/policyNewsView.do?newsId=148930496) |
| 금지되는 것은 이성 혼숙이다 | "또한 청소년은 이성과 함께 혼숙할 수 없습니다." | 같은 곳 |
| 혼숙 적발 시 근무자는 청소년보호법, 업주는 공중위생관리법으로 처분받는다 | "청소년은 퇴실해야 하고 투숙을 허용한 근무자는 「청소년보호법」에 따라 형사처분이, 업주는 「공중위생관리법」에 따라 행정처분이 부과됩니다." | 같은 곳 |
| 동의서가 있어도 업소가 거절할 수 있다 | "보호자의 숙박동의서가 있더라도 숙박업소 자체 규정에 따라 숙박을 거절할 수도 있습니다." | 같은 곳 |

## 2. 글로벌 연동 규격

| 규격 | 사실 | 인용 | 원문 |
|---|---|---|---|
| Booking.com Demand API | **객실마다 성인이 최소 1명 있어야 한다** | "Each room must contain at least one adult." (절 "4. Input guest details and room allocation") | [Search for accommodation](https://developers.booking.com/demand/docs/accommodations/search-for-available-properties) |
| 같은 규격 | 아동은 나이 배열로 싣고, 없으면 그 항목을 생략한다 | "In `guests.children`, if any children will be staying, add the age of each child who should occupy the room." (같은 절) | 같은 곳 |
| 같은 규격 | 아동의 나이 경계는 기본값이 있고 숙소가 자기 값을 정할 수 있다. 요금이 나이 구간에 따라 다르다 | 요지만 (문장 표기 확인 못 함) | [Children policies](https://developers.booking.com/demand/docs/accommodations/child-policies) |
| Expedia Rapid (Lodging) | 아동을 받는 연동이면 **아동 한 명 한 명의 나이**를 넣을 수 있어야 한다 | "If your integration permits child travelers, you must offer the ability to specify the age of each child traveler at the time of check in." (절 "Search Page - Set accurate search parameters (SP1)") | [Lodging API launch requirements](https://developers.expediagroup.com/rapid/setup/launch-requirements/lodging-launch-reqs) |
| 같은 규격 | 아동의 나이 범위를 숙소마다 정한다 | "Each hotel configures what age range they classify as a child. Typically individuals aged 17 or younger are considered children." (같은 절) | 같은 곳 |
| 같은 규격 | 18세 미만 투숙객을 받지 않는 숙소가 있다 | "Some properties do not allow guests under age 18." (같은 절) | 같은 곳 |
| Amadeus Hotel Search | `adults` 파라미터의 허용 범위와 필수 여부 | **확인 못 함** — 문서 페이지 본문이 비어 돌아왔다 (자바스크립트로 그리는 문서). 검색 결과 요약은 1~9 범위라고 하지만 원문을 열지 못해 인용하지 않는다 | [API Reference](https://developers.amadeus.com/self-service/category/hotels/api-doc/hotel-search/api-reference) |

## 3. 숙소의 최소 투숙 연령 (2차 출처)

숙박업계 설명 글 여러 곳이 미국 숙소의 최소 투숙 연령을 18~21세로 적고,
이유로 **체크인이 계약 행위이고 미성년자와 맺은 계약은 업소가 강제할 수 없다는 점**과
숙소가 투숙객에 대해 지는 보호 의무를 든다.

**1차 출처가 아니다.** 법령이나 체인 호텔의 공식 약관을 직접 확인한 것이 아니라 여행 정보 사이트의 설명이므로,
"그런 관행이 있다"까지만 사실로 본다.
