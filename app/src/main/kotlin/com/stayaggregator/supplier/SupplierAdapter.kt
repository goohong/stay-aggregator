package com.stayaggregator.supplier

/**
 * 한 공급사가 구현해야 하는 인터페이스 전부. 공급사마다 한 클래스가 이것을 구현한다 (ADR-0031).
 *
 * consumer 는 이 타입을 보지 않는다. 목록 동기화는 [CatalogAdapter] 목록을, 검색은 [AvailabilityAdapter] 목록을 주입받는다.
 * 이 타입이 있는 이유는 하나다. 공급사 클래스가 두 인터페이스를 함께 구현한다는 관례를 이름으로 드러내는 것이다.
 * **타입만으로는 강제되지 않는다.** `CatalogAdapter` 하나만 구현한 클래스도 컴파일·기동된다. 그래서 [SupplierRegistrationCheck] 가 기동 때,
 * `ArchitectureTest` 가 테스트 때 두 목록이 같은지 확인한다 (ADR-0072).
 * 빠뜨린 채로도 기동은 되고 그 공급사가 한쪽 흐름에서 조용히 빠지기 때문이다 (ADR-0019, ADR-0031).
 */
interface SupplierAdapter : CatalogAdapter, AvailabilityAdapter
