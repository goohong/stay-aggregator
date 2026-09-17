package com.stayaggregator.supplier

import org.springframework.stereotype.Component

/**
 * 공급사 등록이 어긋나면 기동에 실패한다 (ADR-0072).
 *
 * [SupplierAdapter] 는 두 인터페이스를 함께 구현하라는 **관례**일 뿐, 어댑터가 `CatalogAdapter` 하나만 구현해도 컴파일·기동·테스트가 모두 통과한다.
 * 그러면 그 공급사는 목록 동기화에만 등장하고 검색에서는 조용히 빠진다. 반대로 설정 블록(`stay.suppliers.<id>`)만 있고 어댑터가 없어도
 * 조용히 무시됐다. 둘 다 여기서 잡는다. 코드 대신 ArchUnit 테스트로도 지킨다 (`ArchitectureTest`).
 */
@Component
class SupplierRegistrationCheck(
    catalogAdapters: List<CatalogAdapter>,
    availabilityAdapters: List<AvailabilityAdapter>,
    properties: StayProperties,
) {
    init {
        val catalog = catalogAdapters.map { it.supplierId }.toSet()
        val availability = availabilityAdapters.map { it.supplierId }.toSet()
        check(catalog == availability) {
            "두 인터페이스 중 하나만 구현한 공급사가 있다: ${(catalog - availability) + (availability - catalog)}"
        }
        val configured = properties.suppliers.keys
        check(configured == catalog) {
            "설정 블록과 어댑터가 맞지 않는다. 설정만 있음=${configured - catalog}, 어댑터만 있음=${catalog - configured}"
        }
    }
}
