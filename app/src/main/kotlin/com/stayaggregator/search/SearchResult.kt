package com.stayaggregator.search

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
 * 한 공급사의 결과.
 *
 * 상태는 둘뿐이다. **실패는 응답을 아예 만들 수 없었다는 뜻**이다. 매핑을 읽지 못했거나, 모든 묶음이 실패했거나, 시간 한계를 넘겼다.
 * 묶음 일부가 실패한 것은 성공이고 [failedChunks] 에 센다 (ADR-0050).
 * [outOfSpecCount] 는 스펙과 달라 뺀 것만이다. 매핑에 없어 뺀 것은 세지 않는다 (ADR-0046).
 */
data class SupplierResult(
    val supplierId: String,
    val status: SupplierStatus,
    val roomTypes: List<AvailableRoomType>,
    /** 스펙과 달라 응답에서 뺀 객실 타입 수 */
    val outOfSpecCount: Int,
    /** 부르지 못한 묶음 수. 그 묶음의 숙소는 이 응답에 없다 */
    val failedChunks: Int,
    /** 실패했을 때 왜 실패했는지. 성공이면 null */
    val failureReason: String?,
) {
    init {
        when (status) {
            SupplierStatus.SUCCEEDED -> require(failureReason == null) { "성공한 공급사에 실패 사유가 있다" }
            SupplierStatus.FAILED -> {
                require(failureReason != null) { "실패한 공급사에 사유가 없다" }
                require(roomTypes.isEmpty()) { "실패한 공급사에 항목이 있다" }
            }
        }
    }

    companion object {
        fun succeeded(supplierId: String, roomTypes: List<AvailableRoomType>, outOfSpecCount: Int, failedChunks: Int) =
            SupplierResult(supplierId, SupplierStatus.SUCCEEDED, roomTypes, outOfSpecCount, failedChunks, failureReason = null)

        fun failed(supplierId: String, reason: String, failedChunks: Int = 0) =
            SupplierResult(supplierId, SupplierStatus.FAILED, emptyList(), outOfSpecCount = 0, failedChunks, failureReason = reason)
    }
}

enum class SupplierStatus { SUCCEEDED, FAILED }
