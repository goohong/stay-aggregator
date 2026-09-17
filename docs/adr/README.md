# 결정 기록

이 프로젝트에서 내린 설계·작업 결정을 하나씩 남긴다.
형식과 작성 기준은 [ADR-0001](0001-record-decisions-as-adr.md) 에 있다.

| # | 제목 | 상태 |
|---|---|---|
| [0001](0001-record-decisions-as-adr.md) | 결정 기록 방식과 ADR 작성 기준 정의 | accepted |
| [0002](0002-adopt-kotlin.md) | 구현 언어로 Kotlin 채택 | accepted |
| [0003](0003-publish-claude-md.md) | CLAUDE.md 를 저장소에 공개 | accepted |
| [0004](0004-commit-convention.md) | 커밋 규약 채택 | accepted |
| [0005](0005-separate-mock-module.md) | 앱과 Mock 공급사를 별도 Gradle 모듈로 분리 | accepted |
| [0006](0006-add-dependencies-when-needed.md) | 의존성은 그 기능을 만들 때 추가 | accepted |
| [0007](0007-build-and-framework-versions.md) | 빌드 도구와 프레임워크 버전 채택 | accepted (JVM 타깃은 superseded by [0022](0022-jvm-25.md)) |
| [0008](0008-adopt-feature-spec.md) | Feature Spec 을 두 층 구조의 위층으로 도입 | accepted |
| [0009](0009-reactor-over-coroutines.md) | 공급사 호출 흐름을 코루틴이 아닌 Reactor 로 직접 다룸 | accepted |
| [0010](0010-keep-internal-id-on-merge.md) | 같은 숙소를 병합해도 내부 식별자 유지 | accepted |
| [0011](0011-internal-id-random-uuid.md) | 내부 식별자를 무작위 UUID 로 발급해 저장 | accepted |
| [0012](0012-static-info-from-catalog.md) | 응답의 숙소·객실 타입 정적 정보는 숙소 목록 기준으로 저장해 사용 | accepted |
| [0013](0013-catalog-sync-on-startup-and-interval.md) | 숙소 목록은 앱 기동 시와 일정 주기마다 받아오기로 결정 | accepted |
| [0014](0014-detect-drift-in-search-correct-in-sync.md) | 목록 불일치(drift)는 검색에서 감지하고 교정은 동기화 주기에 맡기기로 결정 | accepted |
| [0015](0015-exclude-unmapped-room-types.md) | 매핑에 없는 객실 타입은 응답에서 빼고 뺀 사실을 남기기로 결정 | accepted |
| [0016](0016-catalog-sync-interval-default-daily.md) | 숙소 목록 갱신 주기는 기본 하루 한 번으로 두고 설정으로 바꿀 수 있게 결정 | accepted |
| [0017](0017-mapping-in-server-rdb.md) | 매핑은 서버형 관계형 DB 에 저장하기로 결정 | accepted |
| [0018](0018-postgresql-for-mapping.md) | 매핑을 저장할 DB 로 PostgreSQL 채택 | accepted |
| [0019](0019-first-catalog-failure-no-special-handling.md) | 공급사 첫 숙소 목록 실패에 별도 장치를 두지 않기로 결정 | accepted |
| [0020](0020-mvc-server-with-webclient.md) | 서버는 Spring MVC 로 두고 공급사 호출에만 WebClient 채택 | accepted |
| [0021](0021-block-on-virtual-threads.md) | 검색 요청은 가상 스레드에서 공급사 응답을 기다리기로 결정 | accepted |
| [0022](0022-jvm-25.md) | JVM 툴체인을 25 로 올리기로 결정 | accepted |
| [0023](0023-multi-night-availability-minimum.md) | 연박 예약 가능 객실 수는 날짜별 잔여 수의 최솟값으로 계산 | accepted |
| [0024](0024-missing-night-as-zero-and-record.md) | 숙박일이 빠진 재고 응답은 그 날을 0 으로 보고 사실을 남기기로 결정 | superseded by [0027](0027-spec-violation-handling-criteria.md) |
| [0025](0025-out-of-spec-inventory-dates.md) | 범위 밖·중복·음수 날짜 재고는 보수적으로 계산하고 사실을 남기기로 결정 | superseded by [0027](0027-spec-violation-handling-criteria.md) |
| [0026](0026-expose-unbookable-as-zero.md) | 예약 불가 상품은 응답에서 빼지 않고 예약 가능 객실 수 0 으로 노출 | accepted |
| [0027](0027-spec-violation-handling-criteria.md) | 스펙과 다른 공급사 응답을 세 가지 질문으로 분류하기로 결정 | accepted |
| [0028](0028-implement-decided-units-first.md) | 결정이 끝난 구현 단위부터 구현하고 남은 설계를 이어가기로 결정 | accepted (Verification 짝 규칙은 superseded by [0029](0029-verification-sweep-instead-of-pairing.md)) |
| [0029](0029-verification-sweep-instead-of-pairing.md) | ADR Verification 은 커밋마다 맞추지 않고 모아서 점검하기로 결정 | accepted |
| [0030](0030-supplier-response-dto-receives-without-validation.md) | 공급사 응답 DTO 는 스펙 필드를 모두 받기만 하고 검증은 내부 로직에 두기로 결정 | accepted |
| [0031](0031-supplier-adapter-boundaries.md) | 공급사 어댑터를 기능별 인터페이스로 나누고 공급사마다 한 클래스로 구현하기로 결정 | accepted |
| [0032](0032-mapping-persistence-with-jdbcclient.md) | 매핑 저장은 Spring JDBC 의 JdbcClient 로 하기로 결정 | accepted |
| [0033](0033-flyway-for-schema-migration.md) | 스키마 변경 도구로 Flyway 채택 | accepted |
| [0034](0034-internal-id-as-uuid-column.md) | 내부 식별자를 uuid 컬럼 타입으로 저장하기로 결정 | accepted |
| [0035](0035-compose-and-testcontainers.md) | 로컬은 Docker Compose, 테스트는 Testcontainers 로 PostgreSQL 을 띄우기로 결정 | accepted |
| [0036](0036-mapping-tables.md) | 매핑 테이블을 두 개로 두고 내부 식별자를 키로 쓰기로 결정 | accepted |
| [0037](0037-missing-catalog-entries-kept-and-marked.md) | 목록에 없는 숙소·객실 타입은 매핑을 남기고 missing_since 를 기록하기로 결정 | accepted |
| [0038](0038-catalog-sync-execution.md) | 목록 동기화의 기동 방식·공급사 설정·트랜잭션 경계 결정 | accepted |
| [0039](0039-catalog-sync-remaining.md) | 목록 동기화 구현 단위의 나머지 결정 (타임아웃 위치·기록 최소 형태·테스트 데이터 정리·Mock 목록 모드) | accepted |
| [0040](0040-package-structure.md) | 패키지를 기능별로 나누고 의존 방향을 한 줄로 정하기로 결정 | accepted |
| [0041](0041-validate-in-constructors.md) | 공급사 응답 값의 검증을 담는 객체의 생성자에 두기로 결정 | accepted |
| [0042](0042-rate-as-tax-included-total.md) | 요금은 세금 포함 기간 전체 총액 하나로 담기로 결정 | accepted |
| [0043](0043-stay-period-value-object.md) | 숙박 구간을 체크인·체크아웃 두 날짜의 값 객체로 표현하기로 결정 | accepted |
| [0044](0044-rate-conditions-as-value-object.md) | 판매 조건을 요금이 가지되 조건 묶음을 별도 값 객체로 두기로 결정 | accepted |
| [0045](0045-search-concurrency-and-budget.md) | 재고·요금 호출을 50개씩 나누고 동시 실행 수와 검색 전체 타임아웃을 두기로 결정 | accepted |
| [0046](0046-supplier-status-in-search-response.md) | 검색 응답에 공급사 상태와 스펙 때문에 뺀 건수를 싣기로 결정 | accepted |
| [0047](0047-request-types-in-supplier-package.md) | 재고·요금 조회의 요청 형태를 supplier 패키지에 두기로 결정 | accepted |
| [0048](0048-guest-count-invariants.md) | 검색 인원의 불변식은 각각 0 이상, 합이 1 이상까지만 두기로 결정 | accepted |
| [0049](0049-supplier-id-on-adapter-only.md) | 공급사 식별자는 어댑터만 갖고 응답 공통 형태에는 싣지 않기로 결정 | accepted |
| [0050](0050-partial-chunk-failure.md) | 한 공급사의 chunk 호출이 일부만 실패하면 성공한 chunk 는 내보내고 실패한 chunk 수를 싣기로 결정 | accepted |
| [0051](0051-retry-transient-supplier-failures.md) | 일시적인 공급사 실패만 재시도하고 재시도 여부는 consumer 가 정하기로 결정 | accepted |
| [0052](0052-no-past-date-check.md) | 체크인일이 지난 날짜인지는 검사하지 않기로 결정 | accepted |
| [0053](0053-mixed-currency-exposure.md) | 통화가 다른 상품을 한 응답에 함께 내보내되 환산하지 않기로 결정 | accepted |
| [0054](0054-exclusion-kind-by-value-only.md) | 제외한 항목은 문제가 된 값 하나로만 종류를 나누기로 결정 | accepted |
| [0055](0055-quarantine-grouped-in-db.md) | 제외한 항목을 같은 문제끼리 그룹화해 DB 에 남기기로 결정 | accepted |
| [0056](0056-circuit-breaker-per-supplier-outside-retry.md) | 서킷 브레이커를 공급사마다 두고 재시도 바깥에서 chunk 의 최종 결과를 세기로 결정 | accepted |
| [0057](0057-resilience4j-reactor-for-circuit-breaker.md) | 서킷 브레이커는 resilience4j-reactor 를 쓰고 재시도·타임아웃은 Reactor 연산자로 두기로 결정 | accepted |
| [0058](0058-same-hotel-confirmed-pairs-only.md) | 동일 숙소는 사람이 확인한 짝만 응답에서 같은 값으로 묶기로 결정 | accepted |
| [0059](0059-quarantine-package.md) | 격리 기록을 새 패키지 quarantine 에 두기로 결정 | accepted |
| [0060](0060-supplier-call-metrics.md) | 공급사 호출 지표를 공급사와 결과로 나눠 Micrometer 로 세고 actuator 로 내보내기로 결정 | accepted |
| [0061](0061-catalog-sync-min-interval.md) | 숙소 목록 갱신 주기의 최소 간격을 1시간으로 두고 설정 검사로 지키기로 결정 | accepted |
| [0062](0062-name-mismatch-warning.md) | 목록과 재고 응답의 이름이 다르면 경고로 격리 기록에 남기기로 결정 | accepted |
| [0063](0063-springdoc-openapi.md) | 검색 API 문서를 springdoc-openapi 로 코드에서 만들기로 결정 | accepted |
| [0064](0064-reservation-proxy-design-only.md) | 예약 대행은 설계만 남기고 결과를 모르는 예약은 같은 멱등 키로 공급사에 물어 확정하기로 결정 | accepted |
| [0065](0065-availability-cache-redis.md) | 재고·요금을 숙소 단위로 정규화한 뒤 Redis 에 캐시하기로 결정 | accepted |
| [0066](0066-connect-timeout-in-shared-webclient.md) | 연결 타임아웃을 호출 타임아웃과 따로 두고 모든 어댑터가 한 함수로 WebClient 를 만들기로 결정 | accepted |
| [0067](0067-rejection-carries-field.md) | 값 객체가 거부할 때 틀린 필드를 함께 알리고 정규화가 그 필드로 문제 값을 정하기로 결정 | accepted |
| [0068](0068-search-values-from-relations.md) | 검색 연동 값을 관계식과 근거로 정하기로 결정 | accepted |
| [0069](0069-domain-package.md) | 공급사와 무관한 값 객체를 domain 패키지로 모으기로 결정 | accepted |
| [0070](0070-supplier-failure-sealed.md) | 공급사 실패 예외를 boolean 성질 대신 sealed 하위 타입으로 나누기로 결정 | accepted |
| [0071](0071-resilience-as-adapter-decorator.md) | 재시도·서킷·지표를 검색 서비스가 아니라 어댑터를 감싸는 Decorator 에 두기로 결정 | accepted |
| [0072](0072-adapter-http-helper-and-archunit.md) | 어댑터의 HTTP 호출 조각을 한 클래스로 고정하고 어댑터 규칙을 기동 검사와 ArchUnit 테스트로 지키기로 결정 | accepted |
| [0073](0073-small-design-fixes.md) | 검색 결과 sealed·정규화 거부 범위·매핑 읽기 인터페이스·격리 쓰기 큐 상한·판정 순서와 경고 기록을 정리하기로 결정 | accepted |
