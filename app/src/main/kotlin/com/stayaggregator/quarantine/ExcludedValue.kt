package com.stayaggregator.quarantine

/**
 * 제외한 항목에서 **문제가 된 값**. 같은 문제로 그룹화하는 기준의 하나다 (ADR-0054).
 *
 * 문제의 모양(없다·범위 밖·하나로 못 정함)은 종류로 두지 않는다. 운영자가 할 일("그 공급사에 그 값을 확인해 달라")이 모양과 무관하고,
 * 모양은 사유 문장에 들어 있어 열어 보면 보인다.
 */
enum class ExcludedValue {
    HOTEL_CODE,
    HOTEL_NAME,
    ROOM_TYPE_CODE,
    ROOM_TYPE_NAME,
    MAX_OCCUPANCY,
    INVENTORY,
    RATE,
    CURRENCY,
    SALE_CONDITIONS,

    /** 매핑에 없다. 다음 동기화에서 끝나는 상태라 응답 건수에는 넣지 않지만 기록은 남긴다 (ADR-0046, ADR-0055) */
    MAPPING,

    /** 값 하나에 걸리지 않는 구조 문제. 예: 팔 수 있는 객실 타입이 하나도 없다 */
    STRUCTURE,

    /** 위 어디에도 넣지 못한 것. 이것이 기록되면 분류를 더해야 한다는 신호다 */
    OTHER,
}
