package com.stayaggregator.supplier

import reactor.core.publisher.Mono

/**
 * 공급사의 숙소 목록 API 를 부르고, 공급사마다 다른 표현을 [FetchedCatalog] 로 바꾼다 (ADR-0031).
 *
 * 여기서 하는 판정은 ADR-0027 의 첫 질문뿐이다. 응답 전체를 스펙대로 읽을 수 있는가이다.
 * 읽을 수 없으면 오류 신호를 보낸다. 항목 하나하나의 값이 쓸 만한지는 목록 동기화 쪽의 정규화가 본다.
 *
 * 재고·요금 조회용 인터페이스와, 둘을 묶어 공급사마다 함께 구현하게 하는 인터페이스는
 * 요금(Q1)·날짜 구간(Q2)이 정해지는 검색 구현 단위에서 이 패키지에 더한다 (ADR-0031, ADR-0040).
 */
interface CatalogAdapter {

    /** 공급사를 가리키는 값. 설정 묶음의 키이자 매핑 테이블에 저장하는 값이다 */
    val supplierId: String

    fun fetchCatalog(): Mono<FetchedCatalog>
}
