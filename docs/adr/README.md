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
| [0014](0014-detect-drift-in-search-correct-in-sync.md) | 목록 어긋남은 검색에서 감지하고 교정은 동기화 주기에 맡기기로 결정 | accepted |
| [0015](0015-exclude-unmapped-room-types.md) | 매핑에 없는 객실 타입은 응답에서 빼고 뺀 사실을 남기기로 결정 | accepted |
| [0016](0016-catalog-sync-interval-default-daily.md) | 숙소 목록 갱신 주기는 기본 하루 한 번으로 두고 설정으로 바꿀 수 있게 결정 | accepted |
| [0017](0017-mapping-in-server-rdb.md) | 매핑은 서버형 관계형 DB 에 저장하기로 결정 | accepted |
| [0018](0018-postgresql-for-mapping.md) | 매핑을 저장할 DB 로 PostgreSQL 채택 | accepted |
| [0019](0019-first-catalog-failure-no-special-handling.md) | 공급사 첫 숙소 목록 실패에 별도 장치를 두지 않기로 결정 | accepted |
| [0020](0020-mvc-server-with-webclient.md) | 서버는 Spring MVC 로 두고 공급사 호출에만 WebClient 채택 | accepted |
| [0021](0021-block-on-virtual-threads.md) | 검색 요청은 가상 스레드에서 공급사 응답을 기다리기로 결정 | accepted |
| [0022](0022-jvm-25.md) | JVM 툴체인을 25 로 올리기로 결정 | accepted |
| [0023](0023-multi-night-availability-minimum.md) | 연박 예약 가능 객실 수는 날짜별 잔여 수의 최솟값으로 판정 | accepted |
| [0024](0024-missing-night-as-zero-and-record.md) | 숙박일이 빠진 재고 응답은 그 날을 0 으로 보고 사실을 남기기로 결정 | superseded by [0027](0027-spec-violation-handling-criteria.md) |
| [0025](0025-out-of-spec-inventory-dates.md) | 범위 밖·중복·음수 날짜 재고는 보수적으로 판정하고 사실을 남기기로 결정 | superseded by [0027](0027-spec-violation-handling-criteria.md) |
| [0026](0026-expose-unbookable-as-zero.md) | 예약 불가 상품은 응답에서 빼지 않고 예약 가능 객실 수 0 으로 노출 | accepted |
| [0027](0027-spec-violation-handling-criteria.md) | 스펙과 다른 공급사 응답을 세 가지 질문으로 판정하기로 결정 | accepted |
| [0028](0028-implement-decided-units-first.md) | 결정이 끝난 구현 단위부터 구현하고 남은 설계를 이어가기로 결정 | accepted (Verification 짝 규칙은 superseded by [0029](0029-verification-sweep-instead-of-pairing.md)) |
| [0029](0029-verification-sweep-instead-of-pairing.md) | ADR Verification 은 커밋마다 맞추지 않고 모아서 점검하기로 결정 | accepted |
| [0030](0030-supplier-response-dto-receives-without-validation.md) | 공급사 응답 DTO 는 스펙 필드를 모두 받기만 하고 판정은 내부 로직에 두기로 결정 | accepted |
| [0031](0031-supplier-adapter-boundaries.md) | 공급사 어댑터를 기능별 경계로 나누고 공급사마다 한 클래스로 구현하기로 결정 | accepted |
| [0032](0032-mapping-persistence-with-jdbcclient.md) | 매핑 저장은 Spring JDBC 의 JdbcClient 로 하기로 결정 | accepted |
| [0033](0033-flyway-for-schema-migration.md) | 스키마 변경 도구로 Flyway 채택 | accepted |
| [0034](0034-internal-id-as-uuid-column.md) | 내부 식별자를 uuid 컬럼 타입으로 저장하기로 결정 | accepted |
| [0035](0035-compose-and-testcontainers.md) | 로컬은 Docker Compose, 테스트는 Testcontainers 로 PostgreSQL 을 띄우기로 결정 | accepted |
| [0036](0036-mapping-tables.md) | 매핑 테이블을 두 개로 두고 내부 식별자를 키로 쓰기로 결정 | accepted |
| [0037](0037-missing-catalog-entries-kept-and-marked.md) | 목록에서 사라진 숙소·객실 타입은 매핑을 남기고 사라진 시각을 표시하기로 결정 | accepted |
| [0038](0038-catalog-sync-execution.md) | 목록 동기화의 기동 방식·공급사 설정·트랜잭션 경계 결정 | accepted |
