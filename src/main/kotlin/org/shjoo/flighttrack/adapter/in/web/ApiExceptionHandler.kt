package org.shjoo.flighttrack.adapter.`in`.web

import org.shjoo.flighttrack.domain.model.InvalidRequestException
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(InvalidRequestException::class)
    fun invalidRequest(e: InvalidRequestException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("invalid_request", e.message ?: "invalid request"))

    @ExceptionHandler(ServerWebInputException::class)
    fun badInput(e: ServerWebInputException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("invalid_request", e.reason ?: "invalid request"))

    @ExceptionHandler(UpstreamUnavailableException::class)
    fun upstreamUnavailable(e: UpstreamUnavailableException): ResponseEntity<ErrorResponse> {
        log.warn(e.message)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse("upstream_unavailable", e.message ?: "upstream unavailable"))
    }

    // 존재하지 않는 경로 등 Spring 이 정한 상태 코드를 500 으로 덮어쓰지 않도록 그대로 넘긴다
    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(e: ResponseStatusException): Nothing = throw e

    @ExceptionHandler(Exception::class)
    fun unexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("internal_error", "internal server error"))
    }
}
