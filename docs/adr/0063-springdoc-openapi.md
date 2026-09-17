---
id: 0063
title: 검색 API 문서를 springdoc-openapi 로 코드에서 만들기로 결정
status: accepted
date: 2026-09-17
---

# 0063. 검색 API 문서를 springdoc-openapi 로 코드에서 만들기로 결정

## Context

요구사항은 API 문서 자동화(Swagger/SpringDoc)를 권장한다. 지금 검색 API 계약은 README 의 "검색 API" 절에만 있다.

응답에는 받는 쪽이 꼭 알아야 할 성질이 있다. 예를 들어 `sameHotelId` 는 저장해 두고 다시 찾는 키로 쓰면 안 되고([ADR-0058](0058-same-hotel-confirmed-pairs-only.md)),
`status` 의 `FAILED` 는 "그 공급사 결과를 아예 만들지 못했다"는 뜻이다([ADR-0050](0050-partial-chunk-failure.md)).

## Options

- **(가) springdoc-openapi 로 코드에서 만든다** ← 채택 — 코드와 문서가 같이 간다. 필드 설명을 계약으로 남길 곳이 생긴다.
  대신 의존성이 늘고 Boot 버전에 맞는 판을 확인해야 한다
- **(나) README 의 API 절로 충분하다** — 의존성이 늘지 않는다. 대신 코드와 문서가 따로 가 불일치할 수 있다

## Decision

- springdoc-openapi 를 넣고 `/swagger-ui` 와 `/v3/api-docs` 를 연다
- 응답 필드의 뜻 중 받는 쪽이 오해하기 쉬운 것은 스키마 설명으로 적는다
- README 의 API 절은 예시와 요점만 남기고 전체 계약은 생성된 문서를 가리킨다

**이유**

- 계약을 코드 옆에 두어야 필드가 바뀔 때 설명도 함께 바뀐다

## Consequences

**얻는 것**
- 실행 중인 앱에서 계약을 보고 바로 호출해 볼 수 있다

**잃는 것**
- 의존성이 는다. Boot 4 에 맞는 판이 필요하다 — `springdoc-openapi` 3.1.0 이 Boot 4.1.0 을 올렸고 3.1.1 을 썼다 ([릴리스 노트](https://github.com/springdoc/springdoc-openapi/releases), 2026-09-17 확인)
- 스키마는 응답 DTO 에서 나오므로 DTO 의 이름이 곧 계약 문서가 된다

## Discussion

- **AI 주장과 근거** — (가)를 권했다
- **사용자 판단** — (가)로 정했다

## Verification

앱을 띄워 명세와 화면이 실제로 열리는지 (2026-09-17 실행)
  → `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/v3/api-docs` 가 `200`
  → `curl -s -o /dev/null -w "%{http_code}" -L http://localhost:8080/swagger-ui.html` 가 `200`
  → `/v3/api-docs` 본문에 `"/api/v1/stays/search"` 와 파라미터 넷(`checkIn`·`checkOut`·`adults`·`children`)이 들어 있다

문서에 적은 것과 코드가 어긋나지 않는지
  → 스키마를 손으로 쓰지 않고 응답 DTO(`search/SearchResponse.kt`)에서 만든다. 설명만 컨트롤러의 애노테이션에 있다
