package com.stayaggregator.catalog

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 공급사 코드와 내부 식별자의 매핑을 저장한다 (ADR-0032).
 *
 * 한 공급사의 목록 반영은 통째로 반영되거나 통째로 취소된다 (ADR-0038).
 * 트랜잭션이 HTTP 호출을 감싸지 않도록, 공급사 호출은 이 메서드 밖에서 끝낸 뒤 들어온다.
 */
@Repository
class MappingRepository(private val jdbcClient: JdbcClient) {

    @Transactional
    fun applyCatalog(catalog: NormalizedCatalog): AppliedCatalog {
        // 이번 목록에 없는 행을 찾으려고 코드 수천 개를 조건에 넣는 대신,
        // 그 공급사 행을 먼저 모두 "없음"으로 표시하고 이번 목록에 있는 것만 되살린다.
        // 한 트랜잭션 안이라 커밋 전까지 중간 상태는 밖에서 보이지 않는다.
        markAllMissing(catalog.supplierId)

        catalog.hotels.forEach { hotel -> upsertHotel(catalog.supplierId, hotel) }

        val hotelIds = hotelIdsByCode(catalog.supplierId)
        var roomTypeCount = 0
        catalog.hotels.forEach { hotel ->
            val hotelId = hotelIds.getValue(hotel.code)
            hotel.roomTypes.forEach { roomType ->
                upsertRoomType(hotelId, roomType)
                roomTypeCount++
            }
        }
        return AppliedCatalog(
            hotels = catalog.hotels.size,
            roomTypes = roomTypeCount,
            // 되살리기까지 끝난 뒤에 세야 이번 목록에서 실제로 빠진 행이 나온다
            missingHotels = countMissingHotels(catalog.supplierId),
        )
    }

    private fun markAllMissing(supplierId: String) {
        jdbcClient.sql(
            """
            update room_type_mapping r
               set missing_since = coalesce(r.missing_since, now())
              from hotel_mapping h
             where r.internal_hotel_id = h.internal_hotel_id
               and h.supplier = :supplier
            """.trimIndent(),
        ).param("supplier", supplierId).update()

        jdbcClient.sql(
            """
            update hotel_mapping
               set missing_since = coalesce(missing_since, now())
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

    /** 이미 있으면 내부 식별자를 그대로 두고 이름만 갱신한다 (ADR-0011). 새로 만든 식별자는 쓰이지 않고 버려진다 */
    private fun upsertHotel(supplierId: String, hotel: NormalizedHotel) {
        jdbcClient.sql(
            """
            insert into hotel_mapping (internal_hotel_id, supplier, supplier_hotel_code, hotel_name, missing_since)
            values (:id, :supplier, :code, :name, null)
            on conflict (supplier, supplier_hotel_code)
            do update set hotel_name = excluded.hotel_name, missing_since = null
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("supplier", supplierId)
            .param("code", hotel.code)
            .param("name", hotel.name)
            .update()
    }

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

    private fun hotelIdsByCode(supplierId: String): Map<String, UUID> =
        jdbcClient.sql("select supplier_hotel_code, internal_hotel_id from hotel_mapping where supplier = :supplier")
            .param("supplier", supplierId)
            .query { rs, _ -> rs.getString("supplier_hotel_code") to rs.getObject("internal_hotel_id", UUID::class.java) }
            .list()
            .toMap()
}

/** 한 공급사의 목록을 반영한 결과 */
data class AppliedCatalog(
    val hotels: Int,
    val roomTypes: Int,
    /** 반영이 끝난 뒤에도 표시가 남은 숙소 수. 이번 목록에 없는 숙소다 */
    val missingHotels: Int,
)
