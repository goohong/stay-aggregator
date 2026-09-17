-- 격리 기록에 제외가 아닌 경고도 남긴다. 목록과 재고 응답의 이름이 다른 것이 그 예다 (ADR-0062).
-- 같은 문제로 묶는 기준에 구분을 넣는다. 같은 숙소·값이라도 제외와 경고는 다른 문제다.
ALTER TABLE quarantine_record ADD COLUMN record_kind text NOT NULL DEFAULT 'EXCLUDED';

ALTER TABLE quarantine_record DROP CONSTRAINT uk_quarantine_problem;
ALTER TABLE quarantine_record
    ADD CONSTRAINT uk_quarantine_problem UNIQUE NULLS NOT DISTINCT (supplier, hotel_code, room_type_code, excluded_value, record_kind);
