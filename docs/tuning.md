---
topic: 연동 값 가이드
checked: 2026-09-17
---

# 연동 값 가이드

타임아웃·재시도·서킷·격리 보관 기간·목록 갱신 주기·캐시 TTL 처럼 **숫자로 된 설정값**을 한 곳에 모으고,
값마다 어떤 관계에서 나왔고 어느 부분이 판단인지, 조건이 바뀌면 무엇을 재고 어떻게 다시 계산하는지를 적는다.

**수치에 정답은 없다.** 남겨야 할 것은 값이 아니라 근거와 판단 과정이다.
어느 값을 왜 골랐는지(결정과 이유)는 [ADR-0068](adr/0068-search-values-from-relations.md) 을 비롯한 각 ADR 에 있고 여기에 다시 쓰지 않는다.
이 문서는 **값을 바꿔야 할 때 무엇을 보고 어떻게 다시 계산하는지**를 맡는다.

## 이 문서를 읽는 법

- **근거 종류**는 넷이다. **인용**(외부 문서의 값을 원문으로 확인한 것), **측정**(이 저장소에서 잰 값), **계산**(관계식에서 나온 값), **판단값**(외부 근거 없이 정한 값. 이유를 함께 적는다).
  판단값은 반드시 "판단값"이라고 표시한다. 인용은 원문을 열어 확인한 것만 쓰고, 열지 못한 것은 [미확인](#9-확인하지-못한-것) 에 둔다
- 기호는 [2절](#2-값-목록) 표의 괄호 안 기호다. 관계식 번호 ①~⑥ 은 ADR-0068 의 번호를 그대로 쓰고, 이 문서가 더한 관계식은 R 로 시작한다
- **현재 값**은 `app/src/main/resources/application.yml` 에 있는 값이다. **구현 전**인 값은 ADR 로 정했지만 아직 설정 키가 없는 값이다
- 용어는 [용어집](glossary.md) 을 따른다. 동작 서술의 확인처는 각 ADR 의 Verification 절이다

## 1. 한눈에 보기

| 값 | 현재 값 | 근거 종류 | 어디서 정했나 |
|---|---|---|---|
| 검색 전체 타임아웃 (B) | 8s | 인용 − 판단값 | ADR-0068 |
| 재고·요금 호출 타임아웃 (T) | 2s | 계산 + 판단값 | ADR-0068 |
| 연결 타임아웃 (Tc) | 1.1s | 인용 (관계식 ① 이 강제) | [ADR-0066](adr/0066-connect-timeout-in-shared-webclient.md), ADR-0068 |
| 목록 조회 타임아웃 | 30s | 인용 + 판단값 | [ADR-0039](adr/0039-catalog-sync-remaining.md) |
| 공급사당 동시 호출 수 (c) | 4 | 판단값 | [ADR-0045](adr/0045-search-concurrency-and-budget.md), ADR-0068 |
| 5xx·연결 실패 재시도 (r / m / M) | 2회 / 100ms / 1s | 인용 + 판단값 | [ADR-0051](adr/0051-retry-transient-supplier-failures.md), ADR-0068 |
| 요청 한도 초과 재시도 | 1회 / 1s / 1.5s | 인용 + 계산 | ADR-0068 |
| 서킷 실패율 임계값 (f) | 50% | 인용 | ADR-0068 |
| 서킷 슬라이딩 윈도 (w) | 20 | 판단값 | ADR-0068 |
| 서킷 최소 호출 수 (n_min) | 8 | 계산 + 판단값 | ADR-0068 |
| 서킷 열림 유지 시간 (W) | 30s | 인용 | ADR-0068 |
| 반열림 상태에서 허용하는 호출 수 (p) | 4 | 계산 | ADR-0068 |
| 격리 보관 기간 (D) | 30일 | 판단값 | [ADR-0055](adr/0055-quarantine-grouped-in-db.md), ADR-0068 |
| 목록 갱신 주기 (I) / 최소 간격 (I_min) | 24h / 1h | 인용 / 판단값 | [ADR-0016](adr/0016-catalog-sync-interval-default-daily.md), [ADR-0061](adr/0061-catalog-sync-min-interval.md) |
| 캐시 TTL (구현 전) | 60s | 계산(하한) + 판단값 | [ADR-0065](adr/0065-availability-cache-redis.md), ADR-0068 |

## 2. 값 목록

### 2.1 설정 파일에 있는 값

| 이름 (기호) | 설정 키 | 현재 값 | 무엇을 제어하나 |
|---|---|---|---|
| 검색 전체 타임아웃 (B) | `stay.search.timeout` | `PT8S` | 검색 한 건에서 공급사 하나를 기다리는 상한. 넘긴 공급사만 실패로 나간다 (ADR-0045) |
| 재고·요금 호출 타임아웃 (T) | `stay.suppliers.{id}.availability-timeout` | `PT2S` | chunk 호출 하나의 연결부터 응답까지의 상한 (ADR-0045) |
| 연결 타임아웃 (Tc) | `stay.suppliers.{id}.connect-timeout` | `PT1.1S` | 연결 수립만의 상한. 호출 타임아웃보다 짧아야 한다 (ADR-0066) |
| 목록 조회 타임아웃 | `stay.suppliers.{id}.timeout` | `PT30S` | 숙소 목록 API 호출 하나의 상한 (ADR-0039) |
| 공급사당 동시 호출 수 (c) | `stay.search.concurrency-per-supplier` | `4` | 한 공급사에 한꺼번에 내보내는 chunk 수 (ADR-0045) |
| 5xx·연결 실패 재시도 횟수 (r) | `stay.search.retry.max-retries` | `2` | 첫 호출을 뺀 재시도 횟수 (ADR-0051) |
| 최소 백오프 (m) / 최대 백오프 (M) | `stay.search.retry.min-backoff` / `max-backoff` | `PT0.1S` / `PT1S` | 재시도 사이 대기의 기준과 상한. 무작위가 섞인다 (ADR-0051) |
| 요청 한도 초과 재시도 | `stay.search.throttled-retry.max-retries` / `min-backoff` / `max-backoff` | `1` / `PT1S` / `PT1.5S` | 429·`E429` 에만 쓰는 재시도 기준 (ADR-0068) |
| 서킷 실패율 임계값 (f) | `stay.search.circuit-breaker.failure-rate-threshold` | `50` | 최근 chunk 중 실패 비율이 이 이상이면 연다 (ADR-0056) |
| 슬라이딩 윈도 (w) | `…circuit-breaker.sliding-window-size` | `20` | 실패율을 계산할 최근 chunk 수 |
| 최소 호출 수 (n_min) | `…circuit-breaker.minimum-number-of-calls` | `8` | 이만큼 쌓이기 전에는 실패율을 보지 않는다 |
| 열림 유지 시간 (W) | `…circuit-breaker.wait-duration-in-open-state` | `PT30S` | 열린 뒤 호출하지 않는 시간 |
| 반열림 상태에서 허용하는 호출 수 (p) | `…circuit-breaker.permitted-number-of-calls-in-half-open-state` | `4` | HALF_OPEN 에서 시험으로 내보내는 chunk 수 |
| 격리 보관 기간 (D) | `stay.quarantine.retention` | `P30D` | 마지막으로 본 지 이만큼 지난 격리 기록을 목록 동기화 때 지운다 (ADR-0055) |
| 목록 갱신 주기 (I) | `stay.catalog.sync.interval` | 기본 `PT24H` (설정 파일에는 없고 기본값) | 앞선 실행이 끝난 뒤 다음 실행까지 (ADR-0013, ADR-0016) |
| 첫 실행 지연 | `stay.catalog.sync.initial-delay` | 기본 `PT0S` | 기동 직후 첫 동기화까지. ADR-0013 이 "기동 시"로 정한 값이라 판단 카드가 없다 |

설정 키가 없는 값 둘은 결정으로 고정된 값이다.

| 이름 (기호) | 값 | 무엇인가 |
|---|---|---|
| 목록 갱신 최소 간격 (I_min) | 1h | I 가 이보다 짧으면 앱이 기동에 실패한다 (ADR-0061). 설정이 아니라 검사 기준이다 |
| chunk 상한 | 50 | 공급사가 한 번에 받는 숙소 코드 수. **우리가 고르는 값이 아니라 공급사 제약**이다 ([용어집](glossary.md) "chunk", ADR-0045). 판단 카드가 없다 |

### 2.2 결정했지만 구현 전인 값

| 이름 | 정한 값 | 어디서 |
|---|---|---|
| 캐시 TTL | 60s | ADR-0068. 캐시와 원본의 정합성 오차 허용 범위이기도 하다 (ADR-0065) |
| 조기 갱신 강도 β, Redis 조회 타임아웃 | 미정 | ADR-0065 가 "구현할 때 정한다"고 했다. 관계식과 후보는 [5.14](#514-캐시-ttl--60s-구현-전) 에 둔다 |

### 2.3 우리가 정하지 않았지만 동작을 정하는 라이브러리 기본값

| 이름 | 기본값 | 우리 상태 | 왜 중요한가 | 출처 |
|---|---|---|---|---|
| Reactor Netty 공유 커넥션 풀의 최대 연결 수 (P) | **원격 주소마다 500**, 대기 큐 1,000 | 기본값 사용 | c × 동시 검색 수가 이것을 넘어야 대기가 생긴다. ADR-0045 가 처음 적은 "2×CPU, 최소 16" 은 풀을 직접 만들 때의 상수였고 2026-09-17 정정했다 ([8절](#8-계산-실수를-고친-경위)) | S19 |
| 풀 대기 타임아웃 | 45s | 기본값 사용 | 풀이 찬 뒤 기다리는 시간. B(8s) 보다 훨씬 길다. ADR-0066 이 다루지 않는다고 적었다 | S19 |
| Reactor `Retry.backoff` 의 jitter / multiplier | 0.5 / 2 | 기본값 사용 | 대기 최대치 계산(관계식 ②·③)의 전제다 | S20 |
| resilience4j 기본값 f / w / n_min / W / p | 50 / 100 / 100 / 60s / 10 | f 만 같고 나머지는 덮음 | 기본값끼리는 우리 구조에 맞지 않는다(ADR-0068 선택지 (나)) | S13 |
| HikariCP `maximumPoolSize` / `connectionTimeout` | 10 / 30s | 기본값 사용 ([ADR-0021](adr/0021-block-on-virtual-threads.md)) | 검색마다 매핑 읽기 1회, 격리 기록은 비동기 쓰기. 풀이 차면 30초 기다린다 | S26 |
| Lettuce 명령 타임아웃 | 60s | 캐시 구현 때 반드시 덮는다 | B 보다 길어 그대로 두면 Redis 장애가 검색을 8초 붙잡는다 | S25 |

### 2.4 테스트 값

`app/src/test/resources/application.yml` 의 값이다. 운영 지침이 아니라 끊기는 동작을 빨리 보려고 짧게 둔 값이다.
관계식 ①·②·③ 은 테스트 값도 지켜야 설정 객체가 만들어진다.

| 키 | 테스트 값 |
|---|---|
| `timeout` / `availability-timeout` / `connect-timeout` | `PT1S` / `PT0.5S` / `PT0.25S` |
| `search.timeout` / `concurrency-per-supplier` | `PT2S` / `4` |
| `retry` (r / m / M) | `2` / `PT0.01S` / `PT0.05S` |
| `throttled-retry` | `1` / `PT0.02S` / `PT0.03S` |
| `circuit-breaker` (f / w / n_min / W / p) | `50` / `4` / `4` / `PT1S` / `1` |
| `quarantine.retention` | `P30D` |

## 3. 영향 요인

값을 정하는 입력이다. 요인마다 우리가 어떻게 아는지와 지금 아는 값을 적는다.

| # | 요인 | 우리는 어떻게 아는가 | 지금 아는 것 |
|---|---|---|---|
| F1 | **사용자 대기 허용 시간 (U)** | 스펙에 없다. 외부 문헌으로 가정한다. 우리 API 는 화면의 한 단계라 U 전부를 쓸 수 없다 | 10초가 주의를 붙잡아 두는 상한이다 (S1, ADR-0068 이 대조). 3초 이탈 자료는 페이지 로딩 이야기다(S3) |
| F2 | **공급사 응답 지연 분포 (L: p50 / p95 / p99)** | 지표 `stay.supplier.availability` 타이머의 `outcome=success` (ADR-0060). **지금은 분위수를 내보내지 않는다** — 분위수 노출 설정이 없다 ([7.1](#71-먼저-재는-것)) | Mock 은 같은 컴퓨터에서 1.4ms, 지연 모드는 고정값이라 분포가 없다 (S30). 실제 공급사 값은 모른다 |
| F3 | **공급사당 숙소 수 (N)** → chunk 수 n_c = ⌈N/50⌉ | `hotel_mapping` 에서 `missing_since` 가 비어 있는 행을 공급사별로 센다 | Mock 은 A 2·B 1. 스펙이 말하는 큰 규모는 수천 개(ADR-0045) |
| F4 | **동시 검색 수 (K)** | 스펙에 없다. 운영에서는 `http.server.requests` 의 초당 요청 수 × 평균 소요 시간으로 추정한다 | 모른다. 한 번에 한 요청만 쟀다 (S30) |
| F5 | **공급사 호출 한도 (429 / `E429`)** | 스펙에 오류만 있고 수치가 없다 (ADR-0045). 지표의 `outcome` 은 429 를 따로 세지 않아 재시도 로그로 센다 | 공개 문서의 예: 차단 뒤 회복이 보통 1분(S10), 최소 5분 기다리라는 곳(S8) |
| F6 | **재고·요금 변화 빈도 → 허용 오차 창** | 스펙은 "매번 바뀐다"고만 한다. TTL 은 얼마나 오래된 값을 보여도 되는가라는 제품 판단이다 (ADR-0065) | 표시용 가격 캐시는 업계가 분 단위 지연을 받아들이되(S12b "within 15 to 20 minutes"), 예약 직전에는 다시 확인한다(S9, S11) |
| F7 | **재계산 시간 (Δ)** | 캐시 미스 하나를 채우는 시간 = chunk 호출 하나의 지연. 최악은 T | T = 2s |
| F8 | **운영 점검 주기** | 격리 기록을 사람이 보는 주기. 정해지지 않았다 | ADR-0068 은 한 달에 한 번을 가정했다 |
| F9 | **공급사 목록 변경 빈도** | 스펙은 자주 바뀌지 않는다고 한다. [ADR-0014](adr/0014-detect-drift-in-search-correct-in-sync.md) 의 이름 불일치 경고가 쌓이면 그 관측으로 I 를 조정한다 | 공개 문서의 공급사 넷은 하루 한 번을 기준으로 한다 (S31) |
| F10 | **검색 앞 단계 시간 (Δ_pre)** | chunk 호출 전에 검색 한 건이 쓰는 시간. 매핑 읽기(ADR-0021 이 적었듯 검색 전체 타임아웃이 그 읽기를 취소하지 못하지만 시간은 흐른다), 캐시 조회(구현 뒤), 풀 대기(P 가 500 이라 사실상 0) | 숙소 3개 검색 전체 6ms, 400개 12~18ms (S30). 실제 규모의 매핑 조인은 재지 않았다. **처음 관계식에서 이 항을 빠뜨렸다** ([8절](#8-계산-실수를-고친-경위)) |

## 4. 관계식

ADR-0068 이 정한 관계식 ①~⑥ 은 그 ADR 의 표를 본다. 아래는 **값을 다시 계산할 때 쓰려고 이 문서가 더한 관계식**이다. ADR 에 없고 설정 객체가 검사하지도 않는다.
라운드 수 R = ⌈n_c / c⌉.

| # | 관계식 | 뜻 | 근거 |
|---|---|---|---|
| R1 | 검색 시간 ≈ Δ_pre + R × L. 완전한 결과를 보장하려면 Δ_pre + R × T ≤ B | 측정으로 확인한 관계식. chunk 4개에 2.05s, 8개에 4.04s | S30 |
| R2 | 무응답 공급사에서 검색 한 건이 서킷에 세는 chunk 수 = c × (k·T + Δ_pre < B 인 정수 k 의 수), n_c 를 넘지 않음 | T 로 끝난 라운드만 센다. B 로 취소된 라운드는 세지 않는다(ADR-0056 잃는 것). T=2·B=8 이면 3라운드(12개). n_c ≤ c 면 그 공급사는 B 가 아니라 **T 에** 실패로 확정된다 | 계산 |
| R3 | 서킷이 열리기까지 검색 수 = ⌈n_min / min(R2 의 수, n_c)⌉. 즉시 5xx 를 주는 공급사면 검색당 n_c 개 | n_min 을 "몇 건의 검색이 실패한 뒤 열리는가"로 환산 | S13 "if minimumNumberOfCalls is 10, then at least 10 calls must be recorded, before the failure rate can be calculated." |
| R4 | w ≥ 2 · n_min | w 가 n_min 과 같으면 회복 뒤 성공이 창을 밀어내는 데 w 건이 걸리고, 창이 찬 직후의 실패율이 한두 건에 흔들린다 | 판단값 |
| R5 | c × K ≤ P (원격 주소마다 500). 두 공급사가 같은 주소면 c × 공급사 수 × K ≤ 500 | 넘으면 대기 큐(1,000)에 들어가 최대 45초를 기다리고, 큐도 넘으면 즉시 실패한다 | S19. 그 대기가 호출 타임아웃 안에 드는지는 미확인 |
| R6 | 캐시 TTL ≤ 허용 오차 창(F6), TTL ≪ I | 앞은 제품 판단. 뒤는 매핑 변경이 TTL 뒤에 반영되는 것의 실익을 작게 두는 조건 | ADR-0065 이유 |
| R7 | Redis 조회 타임아웃 t_r ≪ L_p50, t_r < Δ_pre 여유 | 캐시 조회가 공급사 호출만큼 느리면 캐시가 없는 것과 같다. 캐시 조회는 chunk 앞이라(ADR-0065 흐름) Δ_pre 에 더해진다 | S25 |
| R8 | D ≥ k × I (k 는 점검 주기 배수), D 의 해상도 = I | 지우는 일이 목록 동기화 때 돌므로 D 는 I 단위로만 정확하다 | ADR-0055 Verification |
| R9 | I ≥ I_min. I 는 앞선 실행이 끝난 시점부터 잰다 | 실행이 겹치지 않으므로 I 가 동기화 소요보다 짧아도 안전하나, 공급사 부담 때문에 I_min 을 둔다 | ADR-0061 |
| R10 | 목록 조회 타임아웃 < 중간 장비 유휴 한계(60초 안팎). 공급사 수 × 목록 조회 타임아웃 ≤ 기동 뒤 매핑이 준비되기를 기다릴 수 있는 시간 | 스케줄러가 단일 스레드라 무응답 공급사 하나가 그 시간만큼 다음 공급사를 막는다 | ADR-0039 |
| R11 | W ≥ 공급사가 회복하는 데 보통 걸리는 시간. 시험 검색 빈도 = 1/W. 회복 뒤 결과를 잃는 검색 수 ≈ W × 검색 유량 | W 가 짧으면 장애 상태인 공급사에 시험 검색이 잦고(그 검색은 최대 B 만큼 느리다), 길면 회복한 공급사를 늦게 쓴다. 손실의 종류가 다르다 | 계산. 회복 시간의 관측값은 없다 |
| R12 | 격리 기록 쓰기 동시 수 + K ≤ HikariCP 풀(10) | 검색마다 매핑 읽기가 커넥션 하나를 잠깐 쓴다. 격리 쓰기가 풀을 채우면 검색이 30초를 기다린다. 지금 격리 쓰기는 동시 수 제한이 없다 (ADR-0055 잃는 것) | S26 |

## 5. 값마다 판단 카드

카드마다 **후보 · 고른 값 · 버린 후보와 이유 · 근거 종류 · 조건이 바뀌면**을 적는다. 고른 이유의 본문은 해당 ADR 에 있다.

### 5.1 검색 전체 타임아웃 B = 8s

- **영향 요인** — U (F1), 클라이언트 왕복·화면 그리기, 우리 조립(측정 6~18ms)
- **관계식** — B 가 다른 모든 검색 값(T, r, m, c, n_min)의 상한이다
- **후보** — 5s / 8s / 10s
- **고른 값** — 8s. 이유는 ADR-0068 표
- **버린 후보** — 10s: 클라이언트 몫이 0 이 되어 화면에서는 10초를 넘긴다. 5s: 3초 이탈 자료(S3 "53% of visits are likely to be abandoned if pages take longer than 3 seconds to load")를 상한으로 읽은 것인데 그 자료는 페이지 로딩이지 검색 결과 대기가 아니고, 관계식 ② 에서 T ≤ 1.5s 가 되어 정상 호출을 자를 위험이 커진다
- **근거 종류** — 10초는 인용. **뺀 2초는 판단값**(측정한 클라이언트 왕복이 없다)
- **조건이 바뀌면** — 클라이언트에서 검색 호출부터 렌더까지 잰 값이 나오면 B = 10 − 그 값. 자체 이탈 측정처럼 U 를 바꾸는 근거가 나오면 그것이 우선

### 5.2 재고·요금 호출 타임아웃 T = 2s (5s → 2.5s → 2s)

- **영향 요인** — L_p99 (F2), B, r·m·M, Δ_pre (F10)
- **관계식** — ①, ②, R1, R2
- **후보** — 5s(이전) / 2.5s / 2s / p99 기반(미측정)
- **고른 값** — 2s. 3 × 2 + 0.45 = 6.45s, B 까지 1.55s 가 Δ_pre 여유다. R2 에서 무응답 공급사가 검색 한 건에 3라운드를 세어 n_min = 8 을 한 번에 넘긴다
- **버린 후보** — 5s: 3 × 5 = 15s > B 라 느린 5xx 사슬이 항상 취소되어 서킷이 세지 않고, 무응답이면 검색 한 건에 1라운드만 센다. 2.5s: 3 × 2.5 + 0.45 = 7.95s 로 B 에 닿아 앞 단계 시간이 조금만 더해져도 사슬이 취소된다([8절](#8-계산-실수를-고친-경위)). p99 기반: 분위수 지표가 아직 없다
- **근거 종류** — 계산(②) + **판단값**(여유 1.55s. 실측 Δ_pre 는 수 ms 이지만 실제 규모의 매핑 조인·캐시 조회를 모른다) + 미확인(p99 가 2s 를 넘으면 정상 호출을 자른다)
- **조건이 바뀌면** — p99 가 나오면 T ≥ p99 를 먼저 만족시키고 ② 를 다시 푼다. p99 > 2s 면 (가) r 을 1 로 줄여 2T + 0.15 + Δ_pre ≤ 8 → T ≤ 3.1s, (나) r = 2 를 두고 느린 5xx 사슬이 취소되는 것을 받아들인다, (다) 캐시로 호출 자체를 줄인다 중 하나를 고른다. Δ_pre 측정값이 1.5s 보다 훨씬 작으면 T 를 2.5s 로 되돌릴 수 있다

### 5.3 연결 타임아웃 Tc = 1.1s (3.1s → 1.1s)

- **영향 요인** — 공급사와의 네트워크 거리, T (관계식 ①)
- **후보** — 3.1s(AWS SDK standard) / 1.1s(AWS SDK in-region) / 1.5s·1.9s(2s 아래 임의 값)
- **고른 값** — 1.1s. T 아래에서 공개된 기본값이다 (ADR-0068 표, S18b)
- **버린 후보** — 3.1s: ① 위반으로 기동 실패. 1.5s·1.9s: 지어낸 값이라 설명할 수 없고, 1.9s 는 T 에 붙어 연결 타임아웃이 하는 일이 거의 없다
- **근거 종류** — 인용 + 계산. **공급사가 같은 리전에 있다는 가정은 확인되지 않았다.** ADR-0066 이 처음 3.1s 를 고른 이유가 그것이었고, 1.1s 로 옮긴 것은 그 이유를 뒤집은 것이 아니라 ① 이 강제한 것이다(ADR-0066 Decision 에 그렇게 적혀 있다)
- **조건이 바뀌면** — 공급사 연결 시간 관측이 나오면 그 p99 의 2~3배. 연결에 1초 넘게 걸리는 공급사라면 T 자체를 다시 봐야 한다(연결에 1초를 쓰면 응답에 1초밖에 남지 않는다)

### 5.4 목록 조회 타임아웃 = 30s

- **영향 요인** — 중간 장비 유휴 한계, 단일 스레드 스케줄러(R10), 목록 크기
- **후보** — 30s / 60s / 90s
- **고른 값** — 30s (ADR-0039). 이번 조사에서 한 공급사의 검색 계열 권장 타임아웃 30초가 더해졌다: "The recommended timeout for Search by methods and Retrieve hotelpage is 30s." (S11)
- **버린 후보** — 90s: 중간 장비가 60초 안팎에서 먼저 끊어 하는 일이 없다(ADR-0039). 60s: 중간 장비 기본값과 같아 어느 쪽이 먼저 끊는지 모른다
- **근거 종류** — 인용(60초 장비 기본값, 30초 권고) + **판단값**(30초 자체는 "장비보다 짧게, 백그라운드 작업이라 여유 있게")
- **조건이 바뀌면** — 실제 목록 API 응답 시간의 p99 × 2. 목록이 커서 30초를 넘으면 값을 올리지 말고 쪽 단위 조회를 검토한다(ADR-0039 다시 볼 것)

### 5.5 공급사당 동시 호출 수 c = 4

- **영향 요인** — N (F3), K (F4), P, 공급사 한도 (F5), B·T (R1)
- **관계식** — R1, R2, R5, ④, ⑤
- **후보** — 4 / 8 / 20 / n_c 에 맞춤
- **고른 값** — 4 유지. ADR-0045 가 처음 든 이유("기본 풀 최소 16 을 넘지 않게")는 사실과 달랐고 2026-09-17 정정했다. 남은 이유는 "공급사에 한꺼번에 보내는 수를 제한하는 값이고, 지금 규모(n_c ≤ 2)에서는 c ≥ 2 면 동작이 같다"이다
- **버린 후보** — 8·20: 지금은 쓸 데가 없고, 한도(F5)를 모르는 채 늘릴 이유가 없다. n_c 에 맞춤: N 이 늘 때 따라 커지는 것은 R1 에 맞지만 한도가 없으면 상한이 없다
- **근거 종류** — **판단값**
- **조건이 바뀌면** — N = 1,000(n_c = 20): R1 에서 Δ_pre + R × T ≤ B → R ≤ 3 → c ≥ 7 → 8. N = 3,000(n_c = 60): c ≥ 20. 어느 경우든 R5 는 K ≤ 25 까지 여유가 있다. 실제 상한은 공급사 한도다. c 를 바꾸면 ④·⑤ 로 p 와 n_min 이 따라간다

### 5.6 5xx·연결 실패 재시도 r = 2, m = 100ms, M = 1s

- **영향 요인** — 실패가 순간적인 비율, 공급사 회복 시간, B (②), 공급사 부하
- **후보** — r: 1 / 2 / 3. m: 50ms / 100ms / 1s. M: 300ms / 1s / 20s
- **고른 값** — r = 2 는 Google SRE 인용(ADR-0051). m = 100ms 는 AWS 의 50ms 와 같은 자릿수이고 ② 안에 들어 유지(ADR-0068 표). M = 1s 는 r = 2 에서는 걸리지 않는 안전장치다(대기 최대치가 150ms·300ms 라 1s 에 닿지 않는다)
- **버린 후보** — r = 3: 4T + 대기 = 8s 를 넘는다. r = 1: 순간적인 실패를 한 번밖에 못 넘긴다(2T + 0.15 = 4.15s 로 여유는 크다. p99 > 2s 일 때의 대안으로 남긴다). m = 50ms: 공급사 재시작 같은 회복에 너무 짧다는 판단. m = 1s: 대기 합이 4.5s 가 되어 ② 에서 T ≤ 1s 가 된다. M = 20s: B 를 넘어 무의미. M = 300ms: 지금과 동작이 같으나 r 을 올리면 바로 걸린다
- **근거 종류** — r 인용, m 인용의 자릿수 + **판단값**, M **판단값**
- **조건이 바뀌면** — 5xx 뒤 회복까지 걸리는 시간 분포가 나오면 m 은 그 중앙값 근처. r 을 올리면 ② 를 다시 풀어 T 를 내려야 한다

### 5.7 요청 한도 초과 재시도 1회, 최소 백오프 1s, 최대 백오프 1.5s

- **영향 요인** — 공급사 한도 회복 시간 (F5), B (③)
- **후보** — 5xx 와 같게 / 1회·1s / 1회·공급사 안내 시간(1분·5분) / 재시도 안 함
- **고른 값** — 1회·1s·1.5s (ADR-0068 표). 2 × 2 + 1.5 = 5.5s
- **버린 후보** — 5xx 와 같게: 429 를 100ms 뒤에 또 부르는 것은 "기다렸다 다시"라는 뜻에 맞지 않고, 한도가 분 단위면(S10 "typically 1 minute") 100ms 는 의미가 없다. 1분·5분 대기: B 를 넘어 검색 안에서 할 수 없다. 재시도 안 함: 한도가 초 단위로 회복되는 공급사에서 chunk 50개를 잃는다
- **근거 종류** — 인용(기준 대기 1,000ms) + 계산(③) + **판단값**(1회. 두 번 부딪히면 초 단위로 회복되지 않는다는 뜻으로 보고 서킷에 맡긴다)
- **조건이 바뀌면** — 공급사가 `Retry-After` 를 주면 그 값이 B 안이면 따르고 넘으면 재시도하지 않는다(ADR-0068 다시 볼 것). 429 로 열린 서킷은 장애로 열린 서킷과 구분하지 않는다

### 5.8 서킷 실패율 임계값 f = 50%

- **영향 요인** — 부분 실패를 얼마나 참을지, chunk 실패의 독립성
- **후보** — 10% / 50% / 100%
- **고른 값** — 50% (resilience4j 기본값, S13)
- **버린 후보** — 10%(Polly 기본값 S14 "FailureRatio=0.1"): 우리 단위는 재시도까지 거친 chunk 의 최종 결과라 순간 실패가 이미 걸러진 뒤다. 10% 면 chunk 20개 중 2개 실패로 열려 부분 실패(ADR-0050)를 서킷이 덮는다. 100%: 반은 실패하는 공급사를 계속 호출한다
- **근거 종류** — 인용(기본값 차용) + 판단(재시도 뒤라는 이유)
- **조건이 바뀌면** — `failedChunks` 가 크지만 서킷은 열리지 않는 상태가 잦으면(ADR-0060 경보 설계의 첫 항목) 내린다

### 5.9 슬라이딩 윈도 w = 20, 최소 호출 수 n_min = 8 (10 → 8)

- **영향 요인** — c (R2), n_c, "몇 건의 검색이 실패한 뒤 열지"
- **관계식** — ⑤, R3, R4
- **후보** — n_min: 100(resilience4j 기본) / 10 / 8(= 2c) / 4(= c). w: 100 / 20 / 16(= 2·n_min)
- **고른 값** — n_min = 8: 무응답 공급사가 검색당 c = 4 개 이상 세므로(R2) 검색 2건 안에 열린다. N ≤ 100 이면 검색당 2개라 4건이고, 각 검색은 T = 2s 에 실패가 확정된다. w = 20: n_min 의 2.5배. 회복 뒤 성공 20건이면 실패가 창에서 다 밀려난다
- **버린 후보** — n_min = 100: N = 100 이면 검색 50건이 실패한 뒤에야 열린다. 10(이전): c 의 배수가 아니라 검색 3건째의 중간에 열려 설명이 어렵다. 4(= c): 검색 한 건의 첫 라운드 실패로 열린다. 순간 장애에 과민하고 Envoy 의 연속 5xx 기본값 5(S15 "consecutive_5xx … Defaults to 5")보다도 적다. w = 16: n_min 과 가까워 창이 찬 직후의 실패율이 한두 건에 흔들린다
- **근거 종류** — 계산(⑤) + **판단값**("두 건", w = 2.5 · n_min)
- **조건이 바뀌면** — c 가 바뀌면 n_min = 2c, w ≥ 2 · n_min 으로 따라간다. 검색 유량이 매우 낮으면(분당 1건 미만) n_min 을 c 로 내려 첫 검색에서 열리게 할 수 있다

### 5.10 반열림 상태에서 허용하는 호출 수 p = 4 (3 → 4)

- **영향 요인** — c, n_c
- **관계식** — ④
- **후보** — 3(이전) / 4(= c) / 10(resilience4j 기본) / n_c
- **고른 값** — p = c = 4. 시험 검색의 첫 라운드가 온전히 나간다 (ADR-0068 이유)
- **버린 후보** — 3: 첫 라운드부터 1개가 거절되어 시험 검색이 `failedChunks = 1` 을 낸다. 10: N ≤ 100 에서는 n_c = 2 라 의미가 없고, N 이 크면 시험 검색이 두 라운드 반을 장애 상태인 공급사에 보낸다. n_c: 시험 검색은 온전하지만 검색 한 건 내내(≤ B) 시험 호출이 간다
- **근거 종류** — 계산
- **조건이 바뀌면** — c 를 따라간다. resilience4j 의 반열림 최대 대기 시간은 기본값이 0(무한, S13)이라 n_c > p 인 공급사는 시험 chunk p 개가 끝날 때까지 반열림에 남고, 그동안 다른 검색의 chunk 는 거절된다(ADR-0056 잃는 것)

### 5.11 열림 유지 시간 W = 30s 고정

- **영향 요인** — 공급사 회복 시간, 검색 유량, 시험 검색의 비용(최대 B 만큼 느림)
- **관계식** — R11
- **후보** — 30s / 60s(resilience4j 기본) / 5분(429 안내) / 점진 증가(30s → 최대 5분)
- **고른 값** — 30s 고정. 근거는 ADR-0068 표와 이유(Envoy·Istio 기본 차단 시간, 손실 종류가 달라 짧은 쪽)
- **버린 후보** — 60s: 30s 보다 근거가 더 있지 않고 회복 뒤 손실이 두 배다. 5분: 한 공급사의 429 안내(S8)이지 장애 회복 시간이 아니고, 검색에서 5분은 그 공급사를 사실상 빼는 것이다. 점진 증가: Envoy·Istio·Microsoft 가 적는 방식이지만 resilience4j 코어 설정에 없어 직접 구현해야 하므로 ADR-0068 이 다시 볼 것으로 남겼다
- **근거 종류** — 인용 + 판단
- **조건이 바뀌면** — 서킷 상태 전이 로그로 OPEN → HALF_OPEN → CLOSED 까지 걸린 시간을 모으면 그것이 회복 시간의 관측값이다. 분포가 나오면 그 중앙값 근처

### 5.12 격리 보관 기간 D = 30일

- **영향 요인** — 점검 주기 (F8), I (R8 의 해상도), 행 수(같은 문제는 한 행이라 매핑 크기를 넘지 않는다, ADR-0055)
- **후보** — 7일 / 14일 / 30일 / 90일
- **고른 값** — 30일. 마지막으로 본 시각이 갱신되므로 30일 동안 재발하지 않은 문제만 지워진다. 대기열 계열 지침은 원천보다 길게 두라는 원칙만 있고 숫자는 없다: "it is a best practice to always set the retention period of a dead-letter queue to be longer than the retention period of the original queue." (S27)
- **버린 후보** — 7일: 주간 점검을 한 번 놓치면 사라진다. 90일: 행 수는 문제 수라 부담이 아니지만 석 달 전 한 번 본 문제를 남길 이유도 없다
- **근거 종류** — **판단값**
- **조건이 바뀌면** — 점검 주기가 정해지면 그 2~3배(주간 → 14~21일, 월간 → 60~90일). I 를 바꾸면 D 의 해상도가 바뀐다

### 5.13 목록 갱신 주기 I = 24h, 최소 간격 I_min = 1h

- **영향 요인** — 목록 변경 빈도 (F9), 목록 크기, 공급사 한도, 이름 불일치 경고의 빈도
- **관계식** — R9, R6 (TTL ≪ I), R8
- **후보** — I: 6h / 24h / 7일. I_min: 없음 / 동기화 소요 / 1h
- **고른 값** — 24h (ADR-0016, 공개 문서의 공급사 넷이 하루 한 번, S31). 1h (ADR-0061, 초·분 단위 실수를 막는 선)
- **버린 후보** — 6h: 촘촘히 둘 근거가 없고 전체 목록을 받는 스펙이라 부담이 4배. 7일: 한 곳이 전체 갱신을 매주로 권하나 변경분을 매일 받는 것과 짝이다(S31). 우리는 변경분 조회가 없다. I_min 없음 / 동기화 소요: ADR-0061 이 버렸다
- **근거 종류** — I 인용, I_min **판단값**
- **조건이 바뀌면** — 특정 공급사의 이름 불일치 경고가 잦으면 그 공급사만 6~12h (설정이 공통이라 그때 공급사별 키로 나눈다). 공급사가 변경분 조회나 웹훅을 주면 방식 자체를 바꾼다

### 5.14 캐시 TTL = 60s (구현 전)

- **영향 요인** — 허용 오차 창 (F6), 적중률(트래픽·인원 조합), Δ (F7)·β (⑥), I (R6)
- **관계식** — ⑥ (하한 9.2s), R6
- **후보** — 30s / 60s / 300s / 15~20분(표시용 가격 캐시 관행)
- **고른 값** — 60s (ADR-0068 표). 우리는 예약 직전 재확인 단계가 없고 조회 시각만 싣는다(ADR-0065). 재확인이 없으니 표시용 관행보다 짧아야 하고, 하한보다는 충분히 길어야 조기 갱신이 TTL 안에 든다. 1분은 "매진된 방이 예약 가능으로 보이는 창"을 사람이 설명할 수 있는 길이다(판단)
- **버린 후보** — 30s: 하한의 3배라 되지만 적중률이 절반이 된다. 300s: 5분 전 재고를 지금 값처럼 내보낸다. 15~20분: 재확인 단계가 있는 쪽의 값이다(S12b, S9b, S11)
- **근거 종류** — **판단값**(제품 판단) + 계산(하한)
- **조건이 바뀌면** — 적중률과 "캐시에서 나간 값이 실제와 달랐던 비율"을 재서, 뒤가 허용치를 넘으면 반으로 줄이고 적중률이 너무 낮으면 두 배로 늘린다. T 가 바뀌면 하한 4.6βT 를 다시 본다

**함께 정할 값 둘 (미정, ADR-0065 가 구현 때로 넘겼다).** 아래는 결정이 아니라 관계식과 초안의 후보다.

| 값 | 관계식 | 초안의 후보 | 왜 그 후보인가 |
|---|---|---|---|
| 조기 갱신 강도 β | ⑥ | 1 | 논문의 기본값: "The parameter β defaults to 1 and already provides effective prevention against cache stampedes." (S23). 만료 직후 같은 키의 공급사 호출이 2건 이상 몰리는 것이 관측되면 1.5 ("increasing β = 1/λ to 1.5 already drops the average stampede size to less than 2.", S23) |
| Redis 조회 타임아웃 t_r | R7 | 200ms | 같은 네트워크의 Redis 왕복이 수 ms 라면 그 수십 배라 순간 지연을 흡수하고, Δ_pre 여유 1.55s 의 13%, B 의 2.5% 다. Redis 왕복을 재지 않은 판단값이다. Lettuce 기본 60초(S25)는 B 를 넘어 반드시 덮는다 |

## 6. 상황별 표

요인 조합마다 값을 다시 계산한 결과다. 표에 없는 조합은 4절과 ADR-0068 의 관계식으로 계산한다. **모든 행에서 U = 10s 를 전제**한다.

### 6.1 검색 경로: 공급사 지연 × 숙소 수

| 상황 | B | T | Tc | r / m / M | c | n_min / w / p | 비고 |
|---|---|---|---|---|---|---|---|
| **A. p99 ≤ 1s, N ≤ 100** (지금 값의 전제) | 8s | 2s | 1.1s | 2 / 100ms / 1s | 4 | 8 / 20 / 4 | n_c ≤ 2 라 c ≥ 2 면 동작이 같다. 무응답 공급사는 T = 2s 에 실패 확정, 검색 4건 뒤 열린다 |
| **B. p99 1~2s, N ≤ 100** | 8s | 2s | 1.1s | 같음 | 4 | 같음 | 여전히 T ≥ p99. p99 가 2s 에 가까우면 정상 호출의 1% 가 잘리는 것을 받아들이는 것이다 |
| **C. p99 > 2s, N ≤ 100** | 8s | **p99 이상 (예 3s)** | 1.1s | **1** / 100ms / 1s | 4 | 8 / 20 / 4 | 2T + 0.15 + Δ_pre ≤ 8 → T ≤ 3.1s. r = 2 를 두려면 느린 5xx 사슬의 취소를 받아들인다(갈림길). 캐시로 호출을 줄이는 것이 근본 대응 |
| **D. p99 ≤ 1s, N ≈ 1,000 (n_c = 20)** | 8s | 2s | 1.1s | 2 / 100ms / 1s | **8** | **16 / 40 / 8** | c = 4 면 R = 5 → 최악 10s > B. R ≤ 3 이려면 c ≥ 7. 무응답 공급사는 검색 1건에 12개를 세어 바로 열린다. **공급사 한도(F5)를 먼저 확인** |
| **E. N ≥ 3,000 (n_c = 60)** | 8s | 2s | 1.1s | 같음 | **20** | 40 / 100 / 20 | 캐시 없이는 R ≤ 3 에 c ≥ 20 이 필요하고 초당 호출이 검색 유량 × 60 이라 한도에 부딪힌다. ADR-0065 가 이 규모를 전제로 한다. 적중률 h 에서 유효 n_c = ⌈N(1−h)/50⌉ 로 D 행을 다시 계산 |
| **F. 공급사가 즉시 5xx 를 반환** (모든 규모) | — | — | — | — | — | 8 / 20 / 4 | 검색당 n_c 개를 센다(R3). N = 100 → 검색 4건, N ≥ 400 → 1건. 재시도 대기(≤ 450ms)만큼 검색이 느려질 뿐 B 에는 닿지 않는다 |

### 6.2 동시 검색 수 K 와 풀

| 상황 | WebClient 풀 | HikariCP | 비고 |
|---|---|---|---|
| K ≤ 10 | 기본(원격 주소마다 500) | 기본 10 | c = 4~20 어디서나 c × K ≤ 500. 매핑 읽기 K 건 + 격리 쓰기 ≤ 10 |
| K ≈ 10~60 | 기본 | **늘림** (K + 격리 쓰기 + 여유) | HikariCP 가 먼저 찬다. S26 의 권장식은 DB 서버 코어 기준이라 DB 쪽 사양이 필요하다 |
| K > 500 / c | 명시 (P ≥ c × K, 풀 대기 타임아웃 ≤ T) | 늘림 | 이 규모면 캐시 없이는 공급사 한도가 먼저 걸린다 |

**서버 CPU 수는 이 표를 바꾸지 않는다.** 공유 풀은 500 고정이고([2.3](#23-우리가-정하지-않았지만-동작을-정하는-라이브러리-기본값)), 요청 처리 스레드는 가상 스레드다(ADR-0021).

### 6.3 캐시: 재고 변화 빈도 × 트래픽 (구현 전)

| 상황 | TTL | β | t_r |
|---|---|---|---|
| 보통 (정한 값 / 후보) | 60s | 1 | 200ms |
| 변화가 잦음 (임박 예약, 매진이 분 단위로 바뀜) | 30s | 1 | 200ms |
| 변화가 드묾, 같은 조건 검색이 반복됨 | 120~300s | 1 | 200ms |
| 스탬피드 관측 (만료 직후 같은 키 호출 몰림) | 그대로 | 1.5 | 200ms |
| Redis 왕복 p99 > 40ms | 그대로 | 그대로 | p99 × 5 |
| Redis 가 공급사보다 느림 (t_r ≥ L_p50) | — | — | 캐시가 하는 일이 없다. Redis 위치부터 본다 (R7) |

### 6.4 백그라운드 작업: 목록 동기화 · 격리 보관

| 상황 | I | I_min | 목록 조회 타임아웃 | D |
|---|---|---|---|---|
| 보통 (지금) | 24h | 1h | 30s | 30일 |
| 이름 불일치 경고가 잦은 공급사 | 6~12h (공급사별 키로 분리) | 1h | 30s | 30일 |
| 목록이 매우 커서 한 번 받는 데 30초 근접 | 24h | 1h | 60초 미만에서 올림. 넘어야 하면 쪽 단위 조회 | — |
| 점검 주기 주간 / 월간 | — | — | — | 14~21일 / 60~90일 |

## 7. 값을 바꿀 때의 절차

### 7.1 먼저 재는 것

| 재는 것 | 어디서 | 어떻게 |
|---|---|---|
| L 의 p50 / p95 / p99 (공급사별) | 지표 `stay.supplier.availability`, `outcome=success` (ADR-0060) | **분위수 노출 설정이 먼저 필요하다.** `application.yml` 에 `management.metrics.distribution.percentiles-histogram.stay.supplier.availability: true` (또는 `percentiles.stay.supplier.availability: 0.5,0.95,0.99`) 를 더한 뒤 `/actuator/metrics/stay.supplier.availability?tag=supplier:a&tag=outcome:success`. 지금 설정에는 분위수 항목이 없다 |
| Δ_pre (검색 앞 단계) | 두 가지 방법 | (가) `http.server.requests`(URI `/api/v1/stays/search`) p99 − R × availability p99 로 추정. (나) 매핑 읽기에 타이머를 하나 더 둔다. (나)가 정확하고, 캐시를 구현하면 캐시 조회 타이머도 같은 자리에 둔다. **T = 2s 의 여유 1.55s 를 이 값으로 확인한다** |
| 타임아웃 비율 | 같은 지표 `outcome=timeout` ÷ 전체 | ADR-0060 |
| 429 빈도 | 재시도 로그 | 지표에는 따로 없다 (F5). 자주 보이면 `outcome` 에 값을 더하는 것을 검토 |
| N (공급사별 활성 숙소 수) | `hotel_mapping` | `WHERE missing_since IS NULL` |
| K | `http.server.requests` | 초당 요청 수 × 평균 소요 시간 |
| 서킷 상태 전이와 OPEN 지속 시간 | 서킷 상태 변경 로그, resilience4j 지표 (ADR-0060) | OPEN → HALF_OPEN → CLOSED 까지 걸린 시간이 공급사 회복 시간의 관측값이 된다 (W 의 근거) |
| 캐시 적중률, Redis 왕복 | 구현 때 지표를 함께 둔다 | ADR-0065 다시 볼 것 |

### 7.2 다시 계산하는 순서

1. **U → B** (5.1). U 가 바뀌지 않았으면 B 는 그대로
2. **L_p99 → T**: T ≥ p99 이면서 ② `(r+1)T + 대기 최대치 + Δ_pre ≤ B`. 충돌하면 r 을 줄이거나(6.1 의 C 행) 캐시로 호출을 줄인다
3. **T → Tc**: ① Tc < T. Tc 가 T 에 가까우면 연결 타임아웃이 하는 일이 없다
4. **N, T, B, Δ_pre → c**: R1 에서 R = ⌈n_c / c⌉ 로 Δ_pre + R × T ≤ B. 공급사 한도가 있으면 c × 검색 유량 ≤ 한도
5. **c, K → 풀**: R5 c × K ≤ 500. 넘으면 풀을 명시하고 풀 대기 타임아웃도 T 아래로
6. **c → n_min = 2c, p = c, w ≥ 2 · n_min** (⑤, ④, R4)
7. **Δ(= T), β → TTL 하한** (⑥). 상한은 제품 판단 (F6)
8. **K → HikariCP 풀, 격리 쓰기 동시 수** (R12)
9. **I → D 의 해상도**, 점검 주기 → D (R8)

### 7.3 고치는 곳

| 값 | 설정 | 문서 |
|---|---|---|
| B, c, r, m, M, 요청 한도 초과 재시도, 서킷 | `app/src/main/resources/application.yml` 의 `stay.search.*` | ADR-0068 의 값 표(값과 근거 종류). 이유가 바뀌면 ADR-0045 (B·c), ADR-0051 (재시도), ADR-0056 (서킷). Feature Spec 결정 로그에는 ADR 링크만 |
| T, Tc, 목록 조회 타임아웃 | `stay.suppliers.{id}.*` (공급사마다) | ADR-0068 (T·Tc), ADR-0066 (Tc 의 관계), ADR-0039 (목록) |
| I, I_min | `stay.catalog.sync.interval` / 검사 기준 | ADR-0016, ADR-0061 |
| D | `stay.quarantine.retention` | ADR-0055, ADR-0068 |
| TTL, β, t_r | 구현 때 정할 키 | ADR-0065, ADR-0068 |
| 테스트 값 | `app/src/test/resources/application.yml` | ①·②·③ 을 지켜야 설정 객체가 만들어진다 |

설정 객체가 검사하는 관계는 ①·②·③ 뿐이다(ADR-0068 선택지 (ㄱ)). ④·⑤ 와 여유 1.55s 는 판단값이라 검사하지 않으므로, 이 문서의 순서를 따르지 않고 바꿔도 기동은 된다.
값을 바꾼 커밋의 `확인:` 에는 7.1 의 측정값 하나를 적는다. 예: `확인: supplier=a outcome=success p99 = 0.8s (24h 창)`.

## 8. 계산 실수를 고친 경위

판단 과정의 일부로 남긴다. 결과는 각 ADR 에 반영돼 있고 여기에는 무엇이 왜 틀렸는지를 적는다.

1. **호출 타임아웃 2.5s → 2s.** 처음 관계식을 `3T + 대기 최대치 ≤ B` 로 세워 T ≤ (8 − 0.45) / 3 ≈ 2.52 → 2.5s 로 골랐다.
   그러나 검색 전체 타임아웃은 chunk 호출만이 아니라 그 앞의 매핑 읽기부터 잰다. 앞 단계 시간(Δ_pre)이 조금만 있어도 3 × 2.5 + 0.45 = 7.95s 인 사슬이 8s 에 닿아 취소되고, 취소된 chunk 는 서킷이 세지 않는다(ADR-0056 잃는 것).
   관계식에 Δ_pre 항을 더해 ② 로 고치고 T = 2s(사슬 6.45s, 여유 1.55s)로 바꿨다. 여유는 판단값이고 [7.1](#71-먼저-재는-것) 의 Δ_pre 측정으로 확인한다 (ADR-0068 Discussion "계산 실수")
2. **연결 타임아웃 3.1s → 1.1s.** T 를 내리자 관계식 ① 에서 3.1s 가 기동 실패가 됐다. 같은 표(S18b)의 in-region 값 1.1s 로 옮겼다.
   ADR-0066 이 "같은 리전인지 모른다"는 이유로 3.1s 를 골랐던 것이므로, 이번 선택은 그 이유를 뒤집은 것이 아니라 ① 이 강제한 것이다. ADR-0066 Decision 에 그렇게 적었다
3. **동시 호출 수의 전제.** ADR-0045 가 "WebClient 기본 풀은 2×CPU, 최소 16" 이라 적고 c = 4 의 이유로 삼았다. Reactor Netty 소스를 열어 보니 공유 풀은 `max(그 상수, 500)` 이고 원격 주소마다 따로다(S19).
   값 4 는 지금 규모에서 여전히 유효하지만 이유 문장이 틀렸다. 앞선 조사의 "K ≤ 4" 계산과 "2 vCPU 대 8 vCPU" 비교도 이 전제 위에 있어 무효가 됐다. ADR-0045 와 [검색 지연 측정](research/search-latency.md) 에 정정을 적고, 이 문서의 [6.2](#62-동시-검색-수-k-와-풀) 를 그에 맞춰 썼다
4. **AWS SDK 재시도 기준 대기의 단서.** "일시 오류 50ms / 스로틀 1,000ms" 를 "AWS SDK 기본값"이라 부르려 했으나, 그 페이지는 2026년 개정본이고 새 재시도 동작에 대한 것이다(S18).
   "공개된 기준 대기"로만 인용하고 단서를 붙였다 (ADR-0068 Discussion "인용 대조에서 드러난 것")

## 9. 확인하지 못한 것

- 실제 공급사의 지연 분포·동시 검색 수·429 한도. 이 셋이 없으면 T·c 는 판단값을 벗어나지 못한다
- Δ_pre 의 실측. 수 ms 만 있고 실제 규모의 매핑 조인은 없다. T = 2s 의 여유 1.55s 는 이 측정으로 확인해야 한다
- 호출 타임아웃이 풀 대기까지 덮는지. 풀이 500 이라 당장은 부딪히지 않는다
- resilience4j 에서 n_min > w 일 때의 동작. 문서에 없다
- Reactor Netty 연결 타임아웃 기본 30초의 레퍼런스 문장은 이 문서를 쓰며 다시 열지 않았다. ADR-0066 이 2026-09-17 에 대조한 것을 그대로 둔다
- Amadeus 의 초당 한도, Hotelbeds 운영 QPS. 1차 문서를 열지 못했거나 문서에 없다. 2차 출처의 숫자는 쓰지 않았다
- Google 의 "1초 → 3초 이탈 확률 +32%" 류 수치. 원문 페이지가 사라져 미확인. 같은 발행처의 다른 원문(S3)을 썼다
- 이 문서의 모든 계산은 앱·Mock 을 새로 띄우지 않고 기존 측정(S30)과 관계식으로만 했다. ADR-0068 Verification 의 실행 관찰(무응답 A 가 2.19초에 실패, `E429` 프록시에서 재시도 1회·1.4초 간격)은 그 ADR 의 것이다

## 10. 출처

원문을 연 날짜는 모두 2026-09-17 이다. 인용문은 위 본문에 있다. ADR-0068 이 대조한 문장은 그 ADR 의 "확인한 사실" 표에 있다.

| # | 출처 | 위치 |
|---|---|---|
| S1 | Nielsen Norman Group, Response Times: The 3 Important Limits — https://www.nngroup.com/articles/response-times-3-important-limits/ | 본문 0.1 / 1 / 10 second 구간 |
| S3 | Google, The Need for Mobile Speed (2016-09-08) — https://blog.google/products/admanager/the-need-for-mobile-speed/ | "3…2…1… gone" 절 |
| S8 | Expedia Group Rapid, Error responses — https://developers.expediagroup.com/rapid/lodging/reference/error-responses | "429 - Rate limit error" 절 |
| S9 | Hotelbeds, Booking API Workflow — https://developer.hotelbeds.com/documentation/hotels/booking-api/workflow/ | "Parsing the results" 절 |
| S9b | Hotelbeds, Cache API — https://developer.hotelbeds.com/documentation/hotels/cache-api/ | "Service Overview" 절 ("CacheAPI is updated hourly") |
| S10 | Booking.com Demand API, Rate limiting — https://developers.booking.com/demand/docs/development-guide/rate-limiting | "Rate limiting" 절 |
| S11 | ETG (Emerging Travel Group), Integration requirements — https://docs.emergingtravel.com/docs/integration-requirements/ | "3.1 Prebook", Search by 타임아웃 문단, 3.2 캐싱 문단 |
| S12b | Google Hotel Prices, Pricing Delivery Modes — https://developers.google.com/hotels/hotel-prices/dev-guide/delivery-mode | "Pull delivery mode" Step 2 ("within 15 to 20 minutes") |
| S13 | resilience4j, CircuitBreaker — https://resilience4j.readme.io/docs/circuitbreaker | 설정 속성 표, Introduction 의 HALF_OPEN 문장, minimumNumberOfCalls 설명 |
| S14 | Polly, Circuit breaker — https://www.pollydocs.org/strategies/circuit-breaker.html | "Defaults" 절 |
| S15 | Envoy, outlier_detection.proto — https://www.envoyproxy.io/docs/envoy/latest/api-v3/config/cluster/v3/outlier_detection.proto | consecutive_5xx, base_ejection_time |
| S18 | AWS SDKs and Tools Reference Guide, Retry behavior — https://docs.aws.amazon.com/sdkref/latest/guide/feature-retry-behavior.html | "Base delays by error type". 페이지 상단에 2026 신규 동작 단서 |
| S18b | 같은 가이드, Smart configuration defaults — https://docs.aws.amazon.com/sdkref/latest/guide/feature-smart-config-defaults.html | 표의 connectTimeoutInMillis 행 (standard / in-region / cross-region / mobile) |
| S19 | Reactor Netty 1.3.7 소스(Gradle 캐시의 `reactor-netty-core-1.3.7-sources.jar`): `TcpResources.create` 의 `Math.max(ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS, 500)`, `PooledConnectionProvider.acquire` 의 원격 주소별 풀 키; 레퍼런스 https://projectreactor.io/docs/netty/release/reference/http-client.html "Connection Pool" 절 (대기 큐 1,000, 대기 타임아웃 45초) | 앱이 쓰는 버전은 `./gradlew :app:dependencies` 로 1.3.7 확인 |
| S20 | Reactor Core, RetryBackoffSpec javadoc — https://projectreactor.io/docs/core/release/api/reactor/util/retry/RetryBackoffSpec.html | jitter ("Defaults to 0.5"), multiplier ("Defaults to 2."), maxBackoff |
| S23 | Vattani, Chierichetti, Lowenstein, Optimal Probabilistic Cache Stampede Prevention, VLDB 2015 — https://www.vldb.org/pvldb/vol8/p886-vattani.pdf | Figure 3 와 캡션 p.891, β = 1.5 문장 p.892 |
| S25 | Lettuce RedisURI.java — https://raw.githubusercontent.com/redis/lettuce/main/src/main/java/io/lettuce/core/RedisURI.java (`DEFAULT_TIMEOUT = 60`, "Default timeout: 60 sec"); Spring Boot Common Application Properties — https://docs.spring.io/spring-boot/appendix/application-properties/index.html (`spring.data.redis.timeout` 의 Default 열이 비어 있다) | |
| S26 | HikariCP README — https://github.com/brettwooldridge/HikariCP#readme (connectionTimeout "Default: 30000 (30 seconds)", maximumPoolSize "Default: 10"); About Pool Sizing — https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing | |
| S27 | Amazon SQS, Dead-letter queues — https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html | "Understanding message retention periods for dead-letter queues" |
| S30 | 이 저장소 [검색 지연 측정](research/search-latency.md) (2026-09-17 측정) | |
| S31 | 이 저장소 [목록 갱신 주기 조사](research/catalog-sync-interval.md) (2026-09-15 조사) | |
