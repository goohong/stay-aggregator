package com.stayaggregator.quarantine

/**
 * 격리 기록에 남길 제외 항목 하나.
 *
 * [source] 는 우리가 읽어 들인 그 항목이다. JSON 으로 바꿔 남긴다 (ADR-0055).
 */
data class QuarantineEntry(
    val supplierId: String,
    val hotelCode: String?,
    val roomTypeCode: String?,
    val value: ExcludedValue,
    val reason: String,
    val source: Any?,
    val kind: RecordKind = RecordKind.EXCLUDED,
)

/** 뺀 것인지, 빼지는 않았지만 알아 둘 것인지 (ADR-0062) */
enum class RecordKind {
    EXCLUDED,

    /** 항목은 내보냈다. 예: 목록과 재고 응답의 이름이 다르다 */
    WARNING,
}
