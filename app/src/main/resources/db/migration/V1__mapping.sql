-- 공급사 코드와 내부 식별자의 매핑. 요금·재고는 저장하지 않는다 (ADR-0017).
-- 내부 식별자는 앱이 만드는 무작위 UUID 이고 (ADR-0011), uuid 타입에 담는다 (ADR-0034).
-- 두 테이블로 나누고 객실 타입은 내부 숙소 식별자로 숙소를 가리킨다 (ADR-0036).
-- missing_since 는 지금 공급사 목록에 없다는 표시다. 값이 없으면 목록에 있다 (ADR-0037).

CREATE TABLE hotel_mapping (
    internal_hotel_id   uuid        PRIMARY KEY,
    supplier            text        NOT NULL,
    supplier_hotel_code text        NOT NULL,
    hotel_name          text        NOT NULL,
    missing_since       timestamptz,
    CONSTRAINT uk_hotel_mapping_supplier_code UNIQUE (supplier, supplier_hotel_code)
);

-- 객실 타입 코드는 그 숙소 안에서만 유일하다.
CREATE TABLE room_type_mapping (
    internal_room_type_id uuid     PRIMARY KEY,
    internal_hotel_id     uuid     NOT NULL REFERENCES hotel_mapping (internal_hotel_id),
    room_type_code        text     NOT NULL,
    room_type_name        text     NOT NULL,
    max_occupancy         int      NOT NULL,
    missing_since         timestamptz,
    CONSTRAINT uk_room_type_mapping_hotel_code UNIQUE (internal_hotel_id, room_type_code)
);
