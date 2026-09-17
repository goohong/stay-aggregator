---
id: 0060
title: 공급사 호출 지표를 공급사와 결과로 나눠 Micrometer 로 세고 actuator 로 내보내기로 결정
status: accepted
date: 2026-09-17
---

# 0060. 공급사 호출 지표를 공급사와 결과로 나눠 Micrometer 로 세고 actuator 로 내보내기로 결정

## Context

요구사항은 연동 지표·모니터링 설계를 권장하고 예로 공급사별 성공률, 응답 지연, 타임아웃 비율을 든다. 설계만도 된다고 한다 (Q18).

지금은 로그뿐이다. 실패 원인 종류는 로그 문장에 예외 클래스 이름으로 남겨, 뒤에 셀 수 있게 해 두었다 ([ADR-0027](0027-spec-violation-handling-criteria.md)).
서킷 브레이커는 resilience4j 를 쓰고 있다 ([ADR-0057](0057-resilience4j-reactor-for-circuit-breaker.md)).

## Options

지표를 나누는 기준
- **(가) 공급사 + 결과(성공, 타임아웃, 공급사 실패, 서킷 열림)** ← 채택 — 요구사항이 든 세 값이 그대로 나온다. 가짓수는 공급사 수 × 4 로 작다.
  대신 실패를 HTTP 상태별로 더 잘게 보려면 로그를 봐야 한다
- **(나) 공급사 + HTTP 상태나 결과 코드** — 더 잘게 본다. 대신 코드 체계가 공급사마다 달라 태그 값이 공급사별로 갈리고 가짓수가 는다

구현 여부
- **(ㄱ) Micrometer 로 세고 actuator 로 내보낸다** ← 채택 — 세는 코드가 실제로 있다
- **(ㄴ) 설계만 문서로** — 의존성이 늘지 않는다. 대신 실제로 세지 않는다

## Decision

- **묶음 호출 하나마다** 걸린 시간과 결과를 센다. 이름은 `stay.supplier.availability`, 태그는 `supplier` 와 `outcome`
- `outcome` 은 넷이다: `success`, `timeout`, `supplier_failure`, `circuit_open`
  - 재시도까지 거친 **최종 결과**를 센다. 서킷이 세는 단위와 같다 ([ADR-0056](0056-circuit-breaker-per-supplier-outside-retry.md))
- 목록 동기화도 공급사마다 결과를 센다. 이름은 `stay.supplier.catalog`, 같은 태그
- 서킷 상태는 resilience4j 의 Micrometer 연결로 함께 내보낸다
- `/actuator/metrics` 와 `/actuator/health` 만 연다. 경보와 대시보드는 이 저장소 범위 밖이라 무엇에 경보를 걸지 설계 문장으로만 남긴다
- 성공률과 타임아웃 비율은 저장하지 않는다. 수집하는 쪽이 `outcome` 별 횟수로 나눠 계산한다

**경보 설계** (구현 없음)
- 공급사의 `supplier_failure` + `timeout` 비율이 서킷 기준보다 낮게 오래 이어질 때 — 서킷은 안 열리지만 결과가 줄고 있다
- `circuit_open` 이 생길 때
- 격리 기록의 행이 빠르게 늘 때 ([ADR-0055](0055-quarantine-grouped-in-db.md))

**이유**

- 요구사항이 든 성공률·지연·타임아웃 비율이 태그 둘로 바로 나온다
- 가짓수를 작게 두어야 지표 저장소에 부담이 없다. 잘게 보는 것은 이미 로그에 원인 클래스 이름이 있다
- Boot 에 Micrometer 가 들어 있어 따로 만들 것이 없다

## Consequences

**얻는 것**
- 공급사별 성공률·응답 지연·타임아웃 비율을 실제 숫자로 본다
- 서킷 상태를 밖에서 볼 수 있다

**잃는 것**
- actuator 의존성이 는다
- HTTP 상태별로는 지표에서 보이지 않는다
- 인스턴스마다 따로 센다. 합치는 것은 수집하는 쪽 몫이다

**다시 볼 것**
- 경보 기준의 값

## Discussion

- **AI 주장과 근거** — (가)·(ㄱ)을 권했다
- **사용자 판단** — (가)·(ㄱ)으로 정했다

## Verification

구현할 때 확인하고 테스트 이름을 여기에 적는다.
