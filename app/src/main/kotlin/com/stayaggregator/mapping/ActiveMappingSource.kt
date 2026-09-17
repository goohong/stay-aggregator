package com.stayaggregator.mapping

/**
 * 검색이 보는 매핑 읽기 인터페이스 (ADR-0073).
 *
 * 검색은 "그 공급사의 검색 대상 매핑 전부"만 필요하고, 그것이 어디서 오는지는 몰라도 된다.
 * 지금 구현은 [MappingRepository](DB) 하나다. 매핑은 하루 단위로 바뀌므로 동기화가 갱신하는 메모리 스냅샷을 붙일 자리이기도 하다.
 * 재시도·서킷 같은 검색 동작 테스트가 DB 없이 돌 수 있게 하는 것도 이 인터페이스다.
 */
interface ActiveMappingSource {
    /** 한 공급사의 검색 대상 매핑. 숙소와 객실 타입 모두 `missing_since` 가 비어 있는 것만 (ADR-0037) */
    fun findActiveHotels(supplierId: String): List<MappedHotel>
}
