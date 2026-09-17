---
id: 0059
title: 격리 기록을 새 패키지 quarantine 에 두기로 결정
status: accepted
date: 2026-09-17
---

# 0059. 격리 기록을 새 패키지 quarantine 에 두기로 결정

## Context

[ADR-0040](0040-package-structure.md) 이 패키지를 넷으로 나누고 의존 방향을 한 줄로 정했다.

> `catalog` 와 `search` 는 `supplier` 와 `mapping` 을 보고, 서로는 보지 않으며, `supplier` 와 `mapping` 은 아무것도 보지 않는다.

[ADR-0055](0055-quarantine-grouped-in-db.md) 가 제외한 항목을 DB 테이블에 남기기로 했다. 이 기록은 목록 동기화(`catalog`)와 검색(`search`)이 함께 쓴다.
넷 중 어디에도 맞지 않는다. `mapping` 은 "매핑 테이블을 아는 유일한 곳"이다.

## Options

- **(가) 새 패키지 `quarantine`** ← 채택 — `catalog`·`search` 가 보고, `quarantine` 은 아무것도 보지 않는다. 의존 방향이 한 줄로 남는다. 대신 패키지가 다섯이 된다
- **(나) `mapping` 에 둔다** — 패키지가 늘지 않는다. 대신 `mapping` 의 뜻이 "DB 테이블을 아는 곳"으로 넓어져 이름과 내용이 불일치한다

## Decision

- 격리 기록의 테이블 접근, 기록하는 일, 문제가 된 값의 종류를 **`quarantine` 패키지**에 둔다
- 의존 방향: **`catalog` 와 `search` 는 `supplier`·`mapping`·`quarantine` 을 보고, 서로는 보지 않으며, `supplier`·`mapping`·`quarantine` 은 아무것도 보지 않는다** (이후 [ADR-0069](0069-domain-package.md) 가 `domain` 을 더해 셋은 `domain` 만 본다)

**이유**

- 두 흐름이 함께 쓰는 것은 두 흐름 아래에 둔다. ADR-0040 이 `mapping` 을 꺼낸 이유와 같다
- `mapping` 의 뜻을 넓히면 "매핑 테이블의 SQL 이 한 패키지에만 있다"는 ADR-0040 의 얻는 것이 흐려진다

## Consequences

**얻는 것**
- 의존 방향이 한 줄이다
- `mapping` 의 뜻이 그대로다

**잃는 것**
- 패키지가 다섯이다

## Discussion

- **AI 주장과 근거** — (가)를 권했다
- **사용자 판단** — (가)로 정했다

## Verification

`quarantine` 이 다른 패키지를 보지 않는지
  → `grep -rn "import com.stayaggregator." app/src/main/kotlin/com/stayaggregator/quarantine` 에 `quarantine` 밖을 가리키는 줄이 없다
  → 지금은 `ArchitectureTest.supplier·mapping·quarantine 은 domain 만 본다` 가 지킨다 (ADR-0072)

  → 2026-09-17 확인. `quarantine` 에는 `com.stayaggregator.` import 가 없다. `catalog`·`search` 가 `quarantine` 을 본다
