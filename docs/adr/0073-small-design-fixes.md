---
id: 0073
title: 검색 결과를 sealed 로, 정규화가 잡는 예외를 값 객체의 거부로 좁히고, 매핑 읽기를 인터페이스 뒤에, 격리 쓰기를 상한 있는 큐로 두기로 결정
status: accepted
date: 2026-09-17
---

# 0073. 검색 결과를 sealed 로, 정규화가 잡는 예외를 값 객체의 거부로 좁히고, 매핑 읽기를 인터페이스 뒤에, 격리 쓰기를 상한 있는 큐로 두기로 결정

## Context

독립 설계 검토(다른 모델, 2026-09-17)의 "지금 고칠 것" 중 크기가 작은 넷을 한 단위로 처리했다. 각각 문제와 결정을 적는다.

## 1. 공급사 결과를 sealed 로

**문제** — `SupplierResult` 가 상태 enum + nullable 사유 + `init` 검사로 "성공이면 사유 없음, 실패면 항목 없음"을 실행 시점에 지켰다. 같은 파일의 `ChunkOutcome` 은 sealed 였다. 한 파일 안에서 방식이 달랐고, 타입으로 지킬 수 있는 것을 실행 시점에 지켰다.

**결정** — `sealed interface SupplierResult { Succeeded(available, outOfSpecCount, failedChunks); Failed(reason, failedChunks) }`. 실패에는 사유 필드가 있고 항목 필드가 없다. 응답 계약의 `status` 는 타입에서 나온다. `SearchResponse.from` 이 `when` 으로 두 갈래를 옮긴다. 응답 JSON 은 바뀌지 않았다.

## 2. 정규화가 잡는 예외를 값 객체의 거부로 좁힘

**문제** — 검색 정규화의 `step` 이 `IllegalArgumentException` 전부를 항목 거부로 삼켰다. `single()` 이 둘을 받거나 `Money.plus` 가 통화 불일치를 거부하는 것처럼 **우리 코드의 결함**이 "공급사 데이터 문제"로 격리 기록에 남았다. [ADR-0067](0067-rejection-carries-field.md) 이 목록 쪽은 "그 객체의 거부만 받는다"로 좁혔는데 검색 쪽은 그대로였다. 값 객체의 거부 예외가 셋(`Money`, `NormalizedHotel`, `NormalizedRoomType`)이라 `catch` 절도 객체마다 늘었다. `Money.Rejected` 는 스택을 채웠다.

**결정** — `domain.Rejected(field, message)` 를 뿌리로 두고 셋과 `AvailableRoomType` 의 거부가 이것을 상속한다. 스택을 채우지 않는다. 검색 정규화의 `step` 은 `Rejected` 만 항목 거부로 받는다. 정규화 자신이 정하는 거부(요청한 날짜가 빠짐 등)는 `refuse(value, reason)` 로 같은 신호를 낸다. 그 밖의 예외는 올라간다.
Fowler 의 Notification 패턴(예외 대신 결과 목록)으로 가는 것은 생성자 검증(ADR-0041)과 검사를 두 번 하게 되어 하지 않았다. 다시 볼 것으로 남긴다.

## 3. 매핑 읽기를 인터페이스 뒤에

**문제** — 검색이 `MappingRepository` 구체 클래스를 주입받아, 재시도·서킷·타임아웃 같은 매핑과 무관한 동작 테스트가 모두 Postgres 컨테이너를 요구했다. ADR-0031 이 어댑터에 적용한 "좁은 인터페이스는 consumer 와 테스트 대역의 의존 구조"라는 원칙이 저장소에는 적용되지 않았다. 매핑은 하루 단위로 바뀌는데 검색마다 그 공급사 매핑 전체를 읽는다.

**결정** — `mapping.ActiveMappingSource { findActiveHotels(supplierId) }` 를 두고 `MappingRepository` 가 구현한다. 검색은 인터페이스를 받는다. 동기화가 갱신하는 메모리 스냅샷 구현은 지금 만들지 않는다. 다시 볼 것이다.

## 4. 격리 쓰기를 쓰기 스레드 하나와 상한 있는 큐로

**문제** — 기록마다 가상 스레드를 띄워 상한이 없었다. 공급사 하나가 응답 형식을 통째로 바꾸면 검색마다 수십 건의 upsert 가 DB 커넥션 풀(기본 10)을 놓고 매핑 읽기와 경쟁한다. "응답을 기다리게 하지 않는다"(ADR-0055)가 간접적으로 깨진다. 종료 때 `awaitTermination` 결과도 버렸다.

**결정** — 쓰기 스레드 하나(가상 스레드)와 `ArrayBlockingQueue(queueCapacity)`. 큐의 단위는 기록 한 묶음(검색이나 동기화 한 번이 낸 항목들)이다. 차면 그 묶음을 버리고 경고 로그와 지표(`stay.quarantine.dropped`)로 남긴다. 버려도 되는 이유는 같은 문제가 다음 검색에서 또 오고 격리 기록이 같은 문제를 한 행으로 그룹화하기 때문이다(ADR-0055). 종료 때 다 쓰지 못하면 남은 묶음 수를 로그로 남긴다.
사용자가 "쓰기 하나 + 상한 있는 큐, 차면 버리고 경고"로 정했다. 상한 1,000 은 판단값이다. 쓰기 스레드 하나가 초당 수백 행을 쓰므로 1,000 묶음이면 공급사 하나가 형식을 바꾼 상황에서도 수 분을 버틴다. 근거와 재계산은 `docs/tuning.md` 에 둔다.
쓰기를 모아 배치로 넣는 것(ADR-0055 의 다시 볼 것)은 이번에도 하지 않았다.

## Consequences

**얻는 것**
- 성공·실패 결과의 불변식을 타입이 지킨다
- 우리 결함이 격리 기록에 섞이지 않는다. 거부 예외가 스택을 채우지 않는다
- 검색 동작 테스트가 DB 없이 돌 수 있는 자리가 생겼다 (지금 테스트는 아직 컨테이너를 쓴다)
- 격리 쓰기가 점유하는 커넥션이 최대 하나다

**잃는 것**
- 설정값이 하나 늘었다 (`stay.quarantine.queue-capacity`)
- 큐가 차면 기록이 빠진다. 지표로만 안다

## Discussion

- **AI 주장과 근거** — 독립 설계 검토의 지적 [6][9][10][12]. AI 가 그대로 진행했다
- **사용자 판단** — 설계 검토의 "지금 고칠 것" 아홉 항목을 그대로 진행하기로 했다. 격리 큐 방식은 앞서 따로 정했다

## Verification

성공·실패 결과가 응답 계약으로 전과 같이 나가는지
  → `SearchControllerTest.응답에 요구된 최소 정보와 공급사별 상태가 실린다` (변경 없이 통과)

정규화가 값 객체의 거부만 항목 거부로 받는지
  → 정규화 안의 검사를 `refuse` 로 바꾸기 전, `require` 하나를 남겨 둔 상태에서 `AvailabilityNormalizerTest.잔여 수가 음수면 그 객실 타입을 뺀다` 가 `IllegalArgumentException` 으로 실패하는 것을 봤다 (2026-09-17). 즉 값 객체의 거부가 아닌 예외는 삼켜지지 않는다.
  **우리 결함이 올라가는 경우를 직접 만드는 테스트는 없다.** 정규화 밖에서 그런 예외를 넣을 자리가 없다

큐가 차면 묶음을 버리고 세는지
  → `QuarantineRecorderTest.큐가 가득 차면 묶음을 버리고 버린 수를 센다`
  → `QuarantineRecorderTest.큐 상한이 1 미만이면 설정을 만들 수 없다`

검색이 매핑 읽기 인터페이스를 받는지
  → `SearchService` 생성자의 `ActiveMappingSource` 타입. 기존 검색 테스트 전부 그대로 통과
