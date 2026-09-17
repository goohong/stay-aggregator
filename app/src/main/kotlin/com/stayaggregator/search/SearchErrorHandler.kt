package com.stayaggregator.search

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 값 객체가 거부한 입력을 400 으로 돌려준다.
 *
 * 검색 입력의 조건은 값 객체의 생성자에 있고 (ADR-0041), 거부 사유는 그 생성자가 낸 문장이다.
 * 여기서는 그 문장을 그대로 싣는다. 사유를 다시 쓰면 조건과 사유가 두 곳에 갈린다.
 *
 * 날짜 형식이나 숫자 형식이 틀린 것은 Spring 이 먼저 거절하고, 그것도 400 이다.
 */
@RestControllerAdvice(assignableTypes = [SearchController::class])
class SearchErrorHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidInput(e: IllegalArgumentException): ErrorResponse = ErrorResponse(e.message ?: "잘못된 요청이다")

    data class ErrorResponse(val message: String)
}
