package com.stayaggregator.mapping

/**
 * 목록 동기화가 정규화를 마치고 매핑에 반영해 달라고 넘기는 형태.
 *
 * 값 검증을 생성자가 한다 (ADR-0041). 조건을 어긴 객체는 만들어지지 않으므로,
 * 이 타입을 받는 쪽은 값이 유효한지 다시 보지 않는다.
 * 어긴 값을 어떻게 처리할지는 만드는 쪽이 정한다. 목록 동기화는 그 항목을 빼고 사유를 남긴다 (ADR-0039).
 */
data class NormalizedHotel(
    val code: String,
    val name: String,
    val roomTypes: List<NormalizedRoomType>,
) {
    /** 검사한 값 중 무엇이 틀렸나. 거부를 받은 쪽이 문장이 아니라 이 값으로 원인을 가른다 (ADR-0067) */
    enum class Field { CODE, NAME, ROOM_TYPES }

    class Rejected(val field: Field, message: String) : IllegalArgumentException(message)

    init {
        if (code.isBlank()) throw Rejected(Field.CODE, "숙소 코드가 없다")
        if (name.isBlank()) throw Rejected(Field.NAME, "숙소명이 없다")
        // 팔 수 있는 객실 타입이 하나도 없는 숙소는 검색에 걸려도 내놓을 것이 없다 (ADR-0041)
        if (roomTypes.isEmpty()) throw Rejected(Field.ROOM_TYPES, "팔 수 있는 객실 타입이 없다")
    }

    companion object {
        /**
         * 값이 없을 수 있는 곳에서 만든다. 공급사 응답이 그런 곳이다.
         *
         * 넘겨받은 목록을 복사해 든다. 그러지 않으면 밖에서 그 목록을 비워 불변식을 지나칠 수 있다.
         */
        fun of(code: String?, name: String?, roomTypes: List<NormalizedRoomType>) =
            NormalizedHotel(code.orEmpty(), name.orEmpty(), roomTypes.toList())
    }
}

data class NormalizedRoomType(
    val code: String,
    val name: String,
    val maxOccupancy: Int,
) {
    /** 검사한 값 중 무엇이 틀렸나 (ADR-0067) */
    enum class Field { CODE, NAME, MAX_OCCUPANCY }

    class Rejected(val field: Field, message: String) : IllegalArgumentException(message)

    init {
        if (code.isBlank()) throw Rejected(Field.CODE, "객실 타입 코드가 없다")
        if (name.isBlank()) throw Rejected(Field.NAME, "객실 타입명이 없다")
        // 한 명도 묵을 수 없는 객실 타입은 팔 수 없다. 재고 수를 다룰 때와 같은 기준이다 (ADR-0027, ADR-0039)
        if (maxOccupancy < 1) throw Rejected(Field.MAX_OCCUPANCY, "최대 수용 인원이 1 미만이다")
    }

    companion object {
        /**
         * 값이 없을 수 있는 곳에서 만든다.
         *
         * **없는 값과 쓸 수 없는 값의 사유를 구분한다.** 없는 인원을 0 으로 바꿔 생성자에 넘기면
         * "없다"가 "1 미만이다"로 기록되어, 로그만 보고 원인을 잘못 짚게 된다.
         * 글자 값은 없는 것과 빈 것이 같은 사유("코드가 없다")라 그대로 넘긴다.
         */
        fun of(code: String?, name: String?, maxOccupancy: Int?): NormalizedRoomType {
            if (maxOccupancy == null) throw Rejected(Field.MAX_OCCUPANCY, "최대 수용 인원이 없다")
            return NormalizedRoomType(code.orEmpty(), name.orEmpty(), maxOccupancy)
        }
    }
}
