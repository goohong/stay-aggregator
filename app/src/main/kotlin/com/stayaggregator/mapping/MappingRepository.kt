package com.stayaggregator.mapping

import com.stayaggregator.domain.NormalizedHotel
import com.stayaggregator.domain.NormalizedRoomType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 공급사 코드와 내부 식별자의 매핑을 저장한다 (ADR-0032).
 *
 * 매핑 테이블을 아는 유일한 곳이다. 목록 동기화가 쓰고 검색이 읽는다 (ADR-0040).
 * 한 공급사의 목록 반영은 통째로 반영되거나 통째로 취소된다 (ADR-0038).
 * 트랜잭션이 HTTP 호출을 감싸지 않도록, 공급사 호출은 이 메서드 밖에서 끝낸 뒤 들어온다.
 */
@Repository
class MappingRepository(private val jdbcClient: JdbcClient) : ActiveMappingSource {

    @Transactional
    fun applyCatalog(supplierId: String, hotels: List<NormalizedHotel>): AppliedCatalog {
        // 이번 목록에 없는 행을 찾으려고 코드 수천 개를 조건에 넣는 대신,
        // 그 공급사 행을 먼저 모두 "없음"으로 표시하고 이번 목록에 있는 것만 missing_since 를 비운다.
        // 한 트랜잭션 안이라 커밋 전까지 중간 상태는 밖에서 보이지 않는다.
        markAllMissing(supplierId)

        var roomTypeCount = 0
        hotels.forEach { hotel ->
            val hotelId = upsertHotel(supplierId, hotel)
            hotel.roomTypes.forEach { roomType ->
                upsertRoomType(hotelId, roomType)
                roomTypeCount++
            }
        }
        return AppliedCatalog(
            hotels = hotels.size,
            roomTypes = roomTypeCount,
            // missing_since 를 비우기까지 끝난 뒤에 세야 이번 목록에서 실제로 빠진 행이 나온다
            missingHotels = countMissingHotels(supplierId),
        )
    }

    /**
     * 한 공급사의 검색 대상 매핑을 전부 읽는다. 숙소와 객실 타입 모두 `missing_since` 가 비어 있는 것만이다 (ADR-0037).
     *
     * 검색 한 건이 호출 전에 한 번 읽어 두고, 응답이 오면 메모리에서 찾는다. 응답 항목마다 다시 묻지 않는다.
     * 객실 타입이 하나도 남지 않은 숙소는 돌려주지 않는다. 물어봐도 내놓을 것이 없다.
     */
    override fun findActiveHotels(supplierId: String): List<MappedHotel> =
        jdbcClient.sql(
            """
            select h.internal_hotel_id, h.supplier_hotel_code, h.hotel_name,
                   r.internal_room_type_id, r.room_type_code, r.room_type_name, r.max_occupancy
              from hotel_mapping h
              join room_type_mapping r on r.internal_hotel_id = h.internal_hotel_id
             where h.supplier = :supplier
               and h.missing_since is null
               and r.missing_since is null
             order by h.supplier_hotel_code, r.room_type_code
            """.trimIndent(),
        )
            .param("supplier", supplierId)
            .query { rs, _ ->
                MappedRow(
                    hotelId = rs.getObject("internal_hotel_id", UUID::class.java),
                    hotelCode = rs.getString("supplier_hotel_code"),
                    hotelName = rs.getString("hotel_name"),
                    roomType = MappedRoomType(
                        internalRoomTypeId = rs.getObject("internal_room_type_id", UUID::class.java),
                        roomTypeCode = rs.getString("room_type_code"),
                        name = rs.getString("room_type_name"),
                        maxOccupancy = rs.getInt("max_occupancy"),
                    ),
                )
            }
            .list()
            .groupBy { it.hotelId }
            .map { (hotelId, rows) ->
                MappedHotel(
                    internalHotelId = hotelId,
                    supplierHotelCode = rows.first().hotelCode,
                    name = rows.first().hotelName,
                    roomTypes = rows.map { it.roomType },
                )
            }

    /** 조인 결과 한 줄. 숙소별로 그룹화하기 전의 형태라 밖으로 나가지 않는다 */
    private data class MappedRow(
        val hotelId: UUID,
        val hotelCode: String,
        val hotelName: String,
        val roomType: MappedRoomType,
    )

    private fun markAllMissing(supplierId: String) {
        jdbcClient.sql(
            """
            update room_type_mapping r
               set missing_since = now()
              from hotel_mapping h
             where r.internal_hotel_id = h.internal_hotel_id
               and h.supplier = :supplier
               and r.missing_since is null
            """.trimIndent(),
        ).param("supplier", supplierId).update()

        jdbcClient.sql(
            """
            update hotel_mapping
               set missing_since = now()
             where supplier = :supplier
               and missing_since is null
            """.trimIndent(),
        ).param("supplier", supplierId).update()
    }

    /** 이번 목록에 없어 표시가 남은 숙소 수 */
    private fun countMissingHotels(supplierId: String): Int =
        jdbcClient.sql("select count(*) from hotel_mapping where supplier = :supplier and missing_since is not null")
            .param("supplier", supplierId)
            .query(Int::class.java)
            .single()

    /**
     * 이미 있으면 내부 식별자를 그대로 두고 이름만 갱신한다 (ADR-0011). 새로 만든 식별자는 쓰이지 않고 버려진다.
     *
     * 객실 타입이 이 숙소를 가리켜야 하므로 내부 식별자를 돌려받는다. 갱신된 행도 `returning` 으로 나온다.
     */
    private fun upsertHotel(supplierId: String, hotel: NormalizedHotel): UUID =
        jdbcClient.sql(
            """
            insert into hotel_mapping (internal_hotel_id, supplier, supplier_hotel_code, hotel_name, missing_since)
            values (:id, :supplier, :code, :name, null)
            on conflict (supplier, supplier_hotel_code)
            do update set hotel_name = excluded.hotel_name, missing_since = null
            returning internal_hotel_id
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("supplier", supplierId)
            .param("code", hotel.code)
            .param("name", hotel.name)
            .query(UUID::class.java)
            .single()

    private fun upsertRoomType(hotelId: UUID, roomType: NormalizedRoomType) {
        jdbcClient.sql(
            """
            insert into room_type_mapping (
                internal_room_type_id, internal_hotel_id, room_type_code, room_type_name, max_occupancy, missing_since
            )
            values (:id, :hotelId, :code, :name, :maxOccupancy, null)
            on conflict (internal_hotel_id, room_type_code)
            do update set room_type_name = excluded.room_type_name,
                          max_occupancy = excluded.max_occupancy,
                          missing_since = null
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("hotelId", hotelId)
            .param("code", roomType.code)
            .param("name", roomType.name)
            .param("maxOccupancy", roomType.maxOccupancy)
            .update()
    }
}

/** 한 공급사의 목록을 반영한 결과 */
data class AppliedCatalog(
    val hotels: Int,
    val roomTypes: Int,
    /** 반영이 끝난 뒤에도 표시가 남은 숙소 수. 이번 목록에 없는 숙소다 */
    val missingHotels: Int,
)
