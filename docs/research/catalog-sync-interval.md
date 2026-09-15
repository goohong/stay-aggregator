---
topic: 숙소 콘텐츠(목록) 갱신 주기와 변경 반영 방식
checked: 2026-09-15
---

# 숙소 콘텐츠 갱신 주기 조사

숙소 목록을 얼마마다 다시 받아올지 정할 때 참고한, 공급사·연동 플랫폼들의 공개 개발 문서 내용이다.
판단은 [ADR-0016](../adr/0016-catalog-sync-interval-default-daily.md) 에 있다.

## 요약

- 공개 문서에서 주기를 밝힌 해외 공급사 네 곳은 모두 **변경분이든 전체든 하루 한 번**을 기준으로 한다
- 두 곳(Agoda, ETG)은 **변경된 것만 매일** 받는 방식을 따로 제공한다. Agoda 는 전체를 매주 받는 작업을 함께 권한다
- 국내 연동 플랫폼 한 곳(ONDA)은 **변경이 생기면 웹훅으로 식별자만 알리고**, 받는 쪽이 콘텐츠 API 를 다시 호출하게 한다
- 변경분 조회도 알림도 없이 **전체 목록 조회만** 있는 곳도 있다(옐로트래블랩스)

## 공급사·플랫폼별

| 곳 | 주기·방식 | 원문 | 출처 |
|---|---|---|---|
| Expedia Rapid | 매일 | "Property content requires daily refreshes." | [1] |
| Hotelbeds Content API | 첫 적재 후 매일, 바뀐 것만(`lastUpdateTime`) | "we recommend on a daily basis" | [2] |
| Agoda (Demand) | 전체는 매주, 바뀐 숙소는 매일 | "Weekly refresh job: Target all hotels …" / "Daily refresh job: Target changed hotels …" / "Full Refresh … Recommended frequency: Weekly" / "Content Update … Recommended frequency: Daily" | [3], [4] |
| ETG (RateHawk) | 전날 바뀐 숙소만 담은 덤프를 매일 | "The call gets the dump with the ETG hotels which content has changed in the previous day." / "Should be updated every day." | [5] |
| ONDA (국내 연동 플랫폼) | 주기 대신 알림. `contents_updated`(숙소·객실·요금제 콘텐츠 변경), `status_updated`(추가·판매 상태 변경), `inventory_updated`(요금·재고) 웹훅. 콘텐츠 알림은 식별자만 주고 받는 쪽이 다시 조회 | "각 target 의 id 를 이용하여 컨텐츠 업데이트 할 수 있도록 각각의 컨텐츠 API 를 호출해 주세요." | [6], [7] |
| 옐로트래블랩스 (국내 숙박 통합 API) | 업체정보는 전체 목록 조회(`page`, `size`, `locale`, `select`, `where`, `order`). 변경분 조회·주기 권장·변경 알림은 문서에 없음 | — | [8] |

## 확인하지 못한 것

- ETG 의 "전체 덤프는 매주" 권장: 검색 요약에만 있었고, 그 내용이 있다는 안내 페이지가 없어져(404) 원문을 보지 못했다
- WebBeds, TBO Holidays: 공개 자료에서 콘텐츠 갱신 주기를 찾지 못했다
- 국내 채널 매니저(야놀자 클라우드, 산하 WINGS 등): 공개 개발 문서를 찾지 못했다
- ONDA 웹훅 누락 시 재시도나 주기적 조회 권장: 문서에 없었다

## 이 저장소의 공급사와 비교

이 저장소가 연동하는 두 공급사의 숙소 목록 API 는 **변경분 조회도 알림도 없이 전체 목록만** 준다.
위 표에서는 옐로트래블랩스와 같은 형태이고, 매일 받는 해외 공급사들과 달리 매번 전체를 받아야 한다.

## 출처

1. https://developers.expediagroup.com/rapid/lodging
2. https://developer.hotelbeds.com/documentation/hotels/content-api/how-use-content-api/
3. https://developer.agoda.com/demand/docs/faq
4. https://developer.agoda.com/demand/docs/content-api
5. https://docs.emergingtravel.com/docs/b2b-api/static-content/retrieve-hotel-incremental-dump/
6. https://developers.onda.me/docs/api/channel/webhook-overview
7. https://developers.onda.me/docs/api/channel/webhook-specifications
8. https://yellotravel.gitbooks.io/ytl-api/content/property/property.html
