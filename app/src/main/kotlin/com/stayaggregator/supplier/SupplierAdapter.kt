package com.stayaggregator.supplier

/**
 * 한 공급사가 구현해야 하는 경계 전부. 공급사마다 한 클래스가 이것을 구현한다 (ADR-0031).
 *
 * consumer 는 이 타입을 보지 않는다. 목록 동기화는 [CatalogAdapter] 목록을, 검색은 [AvailabilityAdapter] 목록을 주입받는다.
 * 이 타입이 있는 이유는 하나다. 공급사 클래스가 한 경계를 빠뜨리면 컴파일에서 드러나게 하는 것이다.
 * 빠뜨린 채로도 기동은 되고 그 공급사가 한쪽 흐름에서 조용히 빠지기 때문이다 (ADR-0019, ADR-0031).
 */
interface SupplierAdapter : CatalogAdapter, AvailabilityAdapter
