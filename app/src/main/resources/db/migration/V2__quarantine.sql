-- 값을 정할 수 없어 뺀 항목을 같은 문제마다 한 행으로 남긴다 (ADR-0055).
-- 같은 문제는 공급사·숙소 코드·객실 타입 코드·문제가 된 값이 같은 것이다 (ADR-0054).
-- 같은 문제가 다시 오면 새 행을 만들지 않고 횟수와 마지막 값을 갱신한다.

CREATE TABLE quarantine_record (
    supplier        text        NOT NULL,
    -- 코드가 없어서 뺀 것도 남긴다. 그래서 비어 있을 수 있다
    hotel_code      text,
    room_type_code  text,
    excluded_value  text        NOT NULL,
    occurrences     bigint      NOT NULL,
    first_seen      timestamptz NOT NULL,
    last_seen       timestamptz NOT NULL,
    last_reason     text        NOT NULL,
    -- 우리가 읽어 들인 항목 하나를 JSON 으로 둔다. 공급사 원문 그대로는 아니다 (ADR-0055)
    last_source     jsonb,
    -- 코드가 비어 있는 행끼리도 같은 문제로 묶어야 해서 null 을 같은 값으로 본다 (PostgreSQL 15 이상)
    CONSTRAINT uk_quarantine_problem UNIQUE NULLS NOT DISTINCT (supplier, hotel_code, room_type_code, excluded_value)
);

-- 목록 동기화가 오래된 행을 지울 때 쓴다
CREATE INDEX ix_quarantine_last_seen ON quarantine_record (last_seen);
