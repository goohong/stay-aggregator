package com.stayaggregator.search

import com.stayaggregator.domain.AvailableRoomType
/**
 * 검색 한 건의 결과. 공급사마다 상태와 건수를 싣고 항목을 합친다 (ADR-0046, ADR-0050).
 *
 * 공급사 하나가 실패해도 나머지로 응답한다. 실패한 공급사는 여기서 드러난다.
 */
data class SearchResult(
    val suppliers: List<SupplierResult>,
) {
    val roomTypes: List<AvailableRoomType>
        get() = suppliers.flatMap { it.roomTypes }
}

/**
 * 한 공급사의 결과. 성공과 실패가 다른 타입이다.
 *
 * **실패는 응답을 아예 만들 수 없었다는 뜻**이다. 매핑을 읽지 못했거나, 모든 chunk 가 실패했거나, 검색 전체 타임아웃을 넘겼다.
 * chunk 일부가 실패한 것은 성공이고 [failedChunks] 에 센다 (ADR-0050).
 *
 * 전에는 상태 enum + nullable 사유 + `init` 검사로 "성공이면 사유 없음, 실패면 항목 없음"을 실행 시점에 지켰다.
 * sealed 로 두면 타입이 지킨다. 실패에는 사유 필드가 있고 항목 필드가 없다 (ADR-0073).
 */
sealed interface SupplierResult {
    val supplierId: String

    /** 호출하지 못한 chunk 수. 그 chunk 의 숙소는 이 응답에 없다 */
    val failedChunks: Int

    /** 응답에 싣는 상태. 타입에서 나온다 */
    val status: SupplierStatus
        get() = when (this) {
            is Succeeded -> SupplierStatus.SUCCEEDED
            is Failed -> SupplierStatus.FAILED
        }

    /** 실패는 항목이 없다 */
    val roomTypes: List<AvailableRoomType>
        get() = when (this) {
            is Succeeded -> available
            is Failed -> emptyList()
        }

    data class Succeeded(
        override val supplierId: String,
        val available: List<AvailableRoomType>,
        /** 스펙과 달라 응답에서 뺀 객실 타입 수. 매핑에 없어 뺀 것은 세지 않는다 (ADR-0046) */
        val outOfSpecCount: Int,
        override val failedChunks: Int,
    ) : SupplierResult

    data class Failed(
        override val supplierId: String,
        /** 왜 실패했는지. 응답에 그대로 싣는다 (ADR-0046) */
        val reason: String,
        override val failedChunks: Int = 0,
    ) : SupplierResult

    companion object {
        fun succeeded(supplierId: String, roomTypes: List<AvailableRoomType>, outOfSpecCount: Int, failedChunks: Int) =
            Succeeded(supplierId, roomTypes, outOfSpecCount, failedChunks)

        fun failed(supplierId: String, reason: String, failedChunks: Int = 0) = Failed(supplierId, reason, failedChunks)
    }
}

enum class SupplierStatus { SUCCEEDED, FAILED }
