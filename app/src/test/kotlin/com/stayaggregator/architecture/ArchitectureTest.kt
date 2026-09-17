package com.stayaggregator.architecture

import com.stayaggregator.supplier.AvailabilityAdapter
import com.stayaggregator.supplier.CatalogAdapter
import com.stayaggregator.supplier.SupplierAdapter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.web.reactive.function.client.WebClient

/**
 * 구현체가 늘 때 사람 눈이 아니라 테스트로 지키는 규칙 (ADR-0072).
 *
 * 패키지 의존 방향은 ADR-0069 의 문장이다. 어댑터 규칙은 ADR-0031·0066 이 "빠뜨리면 드러난다"고 적었지만
 * 실제로는 관례였던 것을 규칙으로 만든 것이다.
 */
@AnalyzeClasses(packages = ["com.stayaggregator"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {

    @ArchTest
    val `domain 은 아무 패키지도 보지 않는다` = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("..supplier..", "..mapping..", "..catalog..", "..search..", "..quarantine..")

    @ArchTest
    val `supplier·mapping·quarantine 은 domain 만 본다` = noClasses().that().resideInAnyPackage("..supplier..", "..mapping..", "..quarantine..")
        .should().dependOnClassesThat().resideInAnyPackage("..catalog..", "..search..")

    @ArchTest
    val `supplier 와 mapping 과 quarantine 은 서로 보지 않는다` = noClasses().that().resideInAPackage("..supplier..")
        .should().dependOnClassesThat().resideInAnyPackage("..mapping..", "..quarantine..")
        .andShould().dependOnClassesThat().resideInAnyPackage("..mapping..", "..quarantine..")

    @ArchTest
    val `catalog 와 search 는 서로 보지 않는다` = noClasses().that().resideInAPackage("..catalog..")
        .should().dependOnClassesThat().resideInAPackage("..search..")
        .orShould().resideInAPackage("..search..").andShould().dependOnClassesThat().resideInAPackage("..catalog..")

    /**
     * 한 인터페이스만 구현한 어댑터는 그 공급사를 검색이나 동기화에서 조용히 빠뜨린다 (ADR-0031 의 이유).
     * 공급사 어댑터는 `supplier` 의 하위 패키지(`supplier.a` 등)에 산다. `supplier` 자체의 감싸는 어댑터(Decorator)는 대상이 아니다
     */
    @ArchTest
    val `공급사 어댑터는 두 인터페이스를 함께 구현한다` = classes().that().resideInAPackage("..supplier..")
        .and().resideOutsideOfPackage("com.stayaggregator.supplier")
        .and(implementEither(CatalogAdapter::class.java, AvailabilityAdapter::class.java))
        .should().implement(SupplierAdapter::class.java)

    private fun implementEither(vararg types: Class<*>) =
        object : com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass>("implement ${types.joinToString(" or ") { it.simpleName }}") {
            override fun test(t: com.tngtech.archunit.core.domain.JavaClass) = types.any { t.isAssignableTo(it) }
        }

    /** 연결 타임아웃과 인증은 한 곳에서만 붙인다 (ADR-0066) */
    @ArchTest
    val `WebClient 는 SupplierWebClient 에서만 만든다` = noClasses().that().haveNameNotMatching(".*SupplierWebClientKt.*")
        .should().callMethod(WebClient::class.java, "builder")

    /** 호출 타임아웃과 실패 변환은 SupplierHttp 가 고정한 순서로만 붙인다 (ADR-0072) */
    @ArchTest
    val `어댑터는 HTTP 응답을 직접 읽지 않는다` = noClasses().that().resideInAPackage("..supplier.(*)..")
        .should().dependOnClassesThat().areAssignableTo(WebClient::class.java)
}
