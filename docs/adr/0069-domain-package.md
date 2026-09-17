---
id: 0069
title: 공급사와 무관한 값 객체를 domain 패키지로 모으기로 결정
status: accepted
date: 2026-09-17
---

# 0069. 공급사와 무관한 값 객체를 domain 패키지로 모으기로 결정

## Context

값 객체가 세 패키지에 흩어져 있었다.

| 값 객체 | 있던 곳 | 그 패키지의 뜻 |
|---|---|---|
| `StayPeriod`, `GuestCount` | `supplier` | 공급사와 만나는 것 전부 ([ADR-0047](0047-request-types-in-supplier-package.md)) |
| `Money`, `Rate`, `RateConditions`, `AvailableRoomType` | `search` | 검색 흐름 |
| `NormalizedHotel`, `NormalizedRoomType` | `mapping` | 매핑 테이블을 아는 유일한 곳 ([ADR-0040](0040-package-structure.md)) |

ADR-0047 은 공용 패키지(나)를 검토하고 버리면서 **다시 볼 조건**을 적어 두었다. "공급사와 무관한 값이 `supplier` 에 더 쌓이는가". 그 뒤 예약 대행 설계([ADR-0064](0064-reservation-proxy-design-only.md))가 "고객이 본 요금(세금 포함 총액)"을 받기로 했다. 예약이 `Money` 를 쓰려면 `search` 를 봐야 하고, 이것은 유스케이스가 다른 유스케이스에 의존하는 방향이다.

독립 설계 검토(다른 모델, 2026-09-17)가 이 지점을 짚었다. "`mapping` 은 매핑 테이블을 아는 곳이라면서 '숙소가 유효한가'라는 도메인 규칙을 소유한다. 예약 대행은 `Money` 때문에 `search` 에 의존하게 된다."
같은 검토가 ADR-0047 이 (나)를 버린 이유 중 "맞추려면 목록 동기화 코드를 건드린다"는 수정량을 근거로 삼은 것이고, [ADR-0049](0049-supplier-id-on-adapter-only.md) 가 "수정이 대가인 건 말이 안 된다"고 스스로 적은 것과 어긋난다고 지적했다.

## Options

- **(가) `domain` 패키지를 만들고 위 여덟을 옮긴다** ← 채택 — "표준 모델은 여기"가 디렉터리로 보인다. 예약 대행이 `search` 를 보지 않아도 된다. 대신 패키지가 여섯이 되고 의존 방향 문장이 길어진다
- **(나) 지금대로 둔다** — ADR-0047 의 다시 볼 조건에 이미 닿았다. 예약 대행 구현 때 옮기면 그때는 이동이 아니라 의존 방향을 되돌리는 일이 된다

## Decision

- `com.stayaggregator.domain` 을 만들고 `StayPeriod`, `GuestCount`, `Money`, `Rate`, `RateConditions`, `AvailableRoomType`, `NormalizedHotel`, `NormalizedRoomType` 을 옮긴다. 이름은 바꾸지 않는다
- 의존 방향은 이렇게 바뀐다. **`catalog` 와 `search` 는 `domain`·`supplier`·`mapping`·`quarantine` 을 보고, 서로는 보지 않는다. `supplier`·`mapping`·`quarantine` 은 `domain` 만 본다. `domain` 은 아무것도 보지 않는다**
- `NormalizedHotel.Rejected`, `NormalizedRoomType.Rejected`, `Money.Rejected` 는 그대로 각 객체 안에 둔다. 한 패키지에 모였으니 공용으로 합칠 수 있지만, 그것은 실패 예외를 정리하는 별도 단위에서 본다

## Consequences

**얻는 것**
- 예약 대행이나 캐시가 요금·숙박 구간을 쓸 때 `search` 나 `supplier` 를 보지 않는다
- 도메인 규칙(값이 유효한가)이 영속 패키지가 아니라 도메인 패키지에 있다

**잃는 것**
- 패키지가 여섯이다. 의존 방향이 한 줄에서 두 줄이 됐다
- ADR-0040·0047·0059 의 의존 방향 문장이 이 ADR 로 바뀐다. 그 문서들은 고치지 않고 이 ADR 을 가리킨다

## Discussion

- **AI 주장과 근거** — 독립 설계 검토가 "지금 고칠 것"으로 권했고, AI 가 옮기는 것을 제안했다
- **사용자 판단** — 설계 검토의 "지금 고칠 것" 아홉 항목을 그대로 진행하기로 했다

## Verification

옮긴 뒤 의존 방향이 위 문장대로인지
  → `grep -rn "import com.stayaggregator\." app/src/main/kotlin/com/stayaggregator/domain` 이 아무것도 찾지 않는다 (2026-09-17 실행, 출력 없음)
  → `grep -rn "import com.stayaggregator\." app/src/main/kotlin/com/stayaggregator/supplier app/src/main/kotlin/com/stayaggregator/mapping app/src/main/kotlin/com/stayaggregator/quarantine | grep -v "\.domain\."` 이 자기 패키지 밖을 가리키지 않는다
  (ArchUnit 테스트로 바꿀 예정. 그 단위에서 테스트 이름으로 교체한다)

기존 테스트가 그대로 통과하는지
  → `./gradlew :app:test` 146건 통과 (이동만이라 새 테스트는 없다)
