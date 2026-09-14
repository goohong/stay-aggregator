---
topic: 숙박 요금 표시와 요금 조건
checked: 2026-09-14
---

# 숙박 요금 표시와 요금 조건 조사

요금 표준 모델을 정할 때 "무엇을 기준값으로 삼고, 어떤 조건을 요금에 붙일지"의
근거로 조사했다. 여기에는 확인한 사실만 적고, 판단은 해당 ADR 에 적는다.

## 요약

- 규제(한국·미국)와 플랫폼 세 곳 모두 **숙박 기간 전체 총액**을 기준으로 삼거나 가장 눈에 띄게 보여준다
- **1박 가격의 정의는 플랫폼마다 다르다.** 세금 포함 총액을 박수로 나눈 곳과, 세금 제외 1박 가격으로 보이는 곳이 있다
- 요금에 붙는 조건으로 **조식 포함 여부와 무료 취소**가 공통으로 쓰인다. 식사를 종류로 나누는 곳도 있다
- 한 객실 타입에 **요금이 여러 개** 붙는다(판매 조건·할인 방식이 다른 요금)
- "세금 포함" 총액이어도 **현장에서 추가로 내는 세금·요금**이 있을 수 있다

## 1. 규제

| | 시행 | 내용 |
|---|---|---|
| 한국 공정거래위원회 전자상거래 소비자보호지침 개정 | 2025-10-24 | 소비자가 가격을 처음 보는 화면(검색 결과, 상품 목록)부터 총액을 표시. 총액은 거부할 수 없는 모든 비용의 합이며, 숙박은 봉사료·세금이 예시로 포함 |
| 미국 FTC 수수료 규정 (숙박·티켓) | 2025-05-12 | 가장 눈에 띄는 가격은 필수 수수료를 모두 넣은 총액이어야 함. 세금은 따로 명확히 알리면 처음에 빼도 됨. 세부 항목(1박 등) 표시는 허용하되 총액보다 눈에 띄면 안 됨 |

## 2. 플랫폼 화면

조회 조건: 오사카, 2026-10-13 체크인 ~ 10-16 체크아웃(3박), 객실 1, 성인 2. 조회일 2026-09-14.

| | 크게 보이는 값 | 함께 보이는 값 | 요금 조건 필터 |
|---|---|---|---|
| 국내 대형 숙박 플랫폼 (해외숙소) | 1박 가격 | 총 3박 금액 (세금 포함) | 무료취소, 조식포함 |
| Trip.com | 1박 가격 | Total price, 3박, 세금·수수료 포함, "Additional charges may apply" | 조식 포함, 무료 취소 / 식사: 조식·석식·조식+석식 / 결제: 선결제·현장결제 / 연박 할인 |
| Airbnb | 총액 (2025-04-21부터 전 세계 기본) | 수수료 포함, 세금은 지역에 따라 포함 | 조회 안 함 |

### 국내 대형 숙박 플랫폼
- 목록의 1박 가격은 **세금 포함 총액을 박수로 나눠 반올림한 값**이다. 세 숙소에서 계산이 맞았다 (310,328 ÷ 3 = 103,442.67 → 103,443)
- 가격 필터의 이름은 "1박 가격"이다
- 숙소 상세에서 **같은 객실 타입 아래 요금이 여러 개** 있다. 예: 같은 더블 룸에 "임박 할인 · 룸온리"와 "3연박 할인 · 룸온리"가 다른 가격으로 있음
- **요금마다** 무료 취소 기한(날짜·시각), 조식 포함 여부, "세금 및 봉사료 포함"이 붙는다
- 판매자 정보가 객실(요금)별로 다르다고 안내한다
- 일본 숙박세는 체크인 시 현장에서 따로 청구될 수 있다고 공지한다

### Trip.com
- 카드의 1박 가격(US$40)이 총액 ÷ 박수(US$133 ÷ 3 = 44.3)와 다르다. 가격 필터 이름이 "Price per room per night (excl. taxes & fees)" 여서 **세금·수수료 제외 1박 가격으로 보인다** (필터 이름으로 추정)
- 총액 아래에 "Additional charges may apply" 가 붙는 숙소가 있다

### Airbnb
- 검색 결과부터 총액을 기본으로 보여주도록 바꿨다. 이전에는 1박 요금 기준 표시였다
- 확인 경로는 언론 보도(CNN)이며, 회사 공식 발표문은 직접 확인하지 못했다

### 확인하지 못한 곳
- 야놀자(NOL) 해외숙소: 검색 입력이 반영되지 않아 요금 화면까지 가지 못했다

## 3. 업계 연동 규격

| 규격 | 요금 기준 | 요금 조건 |
|---|---|---|
| Google 호텔 가격 구조화 데이터 | 숙박 기간 **총액**. 기본 요금·세금·수수료·할인은 구성 항목으로 따로 | 조식 포함(`BreakfastIncluded`), 환불 정책(`hasMerchantReturnPolicy`), 회원 전용 요금 |
| Expedia Rapid Shopping API | — | **요금마다** 취소 조건(`refundable`, `cancel_penalties`) |
| OpenTravel 코드 목록 (Meal Plan Type) | — | 식사 조건을 23종 코드로 구분 (Room only, Bed & breakfast, Half board, Full board 등) |

## 출처

- 한국 공정거래위원회 지침 개정 보도: https://www.korea.kr/news/policyNewsView.do?newsId=156721985
- 미국 FTC 발표: https://www.ftc.gov/news-events/news/press-releases/2024/12/federal-trade-commission-announces-bipartisan-rule-banning-junk-ticket-hotel-fees
- Airbnb 총액 표시 보도 (CNN): https://www.cnn.com/2025/04/21/business/airbnb-total-pricing
- Trip.com 오사카 검색 화면: https://www.trip.com/hotels/list?city=219&checkin=2026-10-13&checkout=2026-10-16&adult=2&crn=1
- Google 호텔 가격 구조화 데이터: https://developers.google.com/hotels/hotel-prices/structured-data/hotel-price-structured-data
- Expedia 취소 정책 구성: https://developers.expediagroup.com/rapid/lodging/shopping/constructing-cancellation-policies
- OpenTravel Meal Plan Type: https://developer.siteminder.com/siteminder-apis/additional-resources/reference-tables/meal-plan-type
- 국내 대형 숙박 플랫폼 해외숙소 화면: 직접 조회 (주소 생략)
