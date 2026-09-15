package com.stayaggregator.mocksupplier

/**
 * Mock 이 돌려주는 고정 응답. 공급사 스펙의 구조를 따르고 값은 직접 만든 것이다.
 *
 * 담은 상황
 * - 같은 숙소·객실을 A(`A-20011`/`DLX-DBL`)와 B(`B40102`/`R-201`)가 각자 코드로 판다.
 *   객실 이름 표기가 다르고, B 만 조식 포함이며, 총액이 다르다
 * - A 에만 있는 숙소(`A-20035`)가 있고 둘째 날 재고가 0 이다
 *
 * 재고·요금은 요청 날짜와 무관하게 2026-10-05 체크인, 2026-10-08 체크아웃(3박) 기준으로 준다.
 */
object SupplierFixtures {

    val A_HOTELS = """
        {
          "items": [
            {
              "hotelCode": "A-20011",
              "hotelName": "Hangang View Hotel",
              "roomTypes": [
                { "roomTypeCode": "DLX-DBL", "roomTypeName": "Deluxe Double", "maxOccupancy": 2 }
              ]
            },
            {
              "hotelCode": "A-20035",
              "hotelName": "Bukchon Hanok Stay",
              "roomTypes": [
                { "roomTypeCode": "STD-TWN", "roomTypeName": "Standard Twin", "maxOccupancy": 2 }
              ]
            }
          ]
        }
    """.trimIndent()

    val A_AVAILABILITY = """
        {
          "items": [
            {
              "hotelCode": "A-20011",
              "hotelName": "Hangang View Hotel",
              "roomTypeCode": "DLX-DBL",
              "roomTypeName": "Deluxe Double",
              "maxOccupancy": 2,
              "breakfastIncluded": false,
              "currency": "KRW",
              "dailyRates": [
                { "date": "2026-10-05", "remainingRooms": 4, "nightlyRate": 110000, "taxAmount": 11000 },
                { "date": "2026-10-06", "remainingRooms": 2, "nightlyRate": 140000, "taxAmount": 14000 },
                { "date": "2026-10-07", "remainingRooms": 6, "nightlyRate": 110000, "taxAmount": 11000 }
              ]
            },
            {
              "hotelCode": "A-20035",
              "hotelName": "Bukchon Hanok Stay",
              "roomTypeCode": "STD-TWN",
              "roomTypeName": "Standard Twin",
              "maxOccupancy": 2,
              "breakfastIncluded": false,
              "currency": "KRW",
              "dailyRates": [
                { "date": "2026-10-05", "remainingRooms": 3, "nightlyRate": 80000, "taxAmount": 8000 },
                { "date": "2026-10-06", "remainingRooms": 0, "nightlyRate": 95000, "taxAmount": 9500 },
                { "date": "2026-10-07", "remainingRooms": 2, "nightlyRate": 80000, "taxAmount": 8000 }
              ]
            }
          ]
        }
    """.trimIndent()

    val A_ERROR = """{ "error": "SERVICE_UNAVAILABLE", "message": "supplier temporarily unavailable" }"""

    val B_PROPERTIES = """
        {
          "resultCode": "0000",
          "resultMessage": "SUCCESS",
          "data": {
            "items": [
              {
                "propertyId": "B40102",
                "propertyName": "Hangang View Hotel",
                "rooms": [
                  { "roomId": "R-201", "roomName": "Deluxe Double Room", "maxOccupancy": 2 }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    val B_SEARCH = """
        {
          "resultCode": "0000",
          "resultMessage": "SUCCESS",
          "data": {
            "items": [
              {
                "propertyId": "B40102",
                "propertyName": "Hangang View Hotel",
                "roomId": "R-201",
                "roomName": "Deluxe Double Room",
                "maxOccupancy": 2,
                "breakfastIncluded": true,
                "currency": "KRW",
                "totalPrice": 431000,
                "taxIncluded": true,
                "inventory": [
                  { "date": "2026-10-05", "remainingRooms": 4 },
                  { "date": "2026-10-06", "remainingRooms": 2 },
                  { "date": "2026-10-07", "remainingRooms": 6 }
                ]
              }
            ]
          }
        }
    """.trimIndent()

    val B_ERROR = """{ "resultCode": "E503", "resultMessage": "TEMPORARILY_UNAVAILABLE", "data": null }"""
}
