package com.obs.api.exception;

import com.obs.api.dto.ErrorDto;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorDto> bad(BadRequestException e, WebRequest r) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), r);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
                       MethodArgumentTypeMismatchException.class,
                       MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorDto> invalid(Exception e, WebRequest r) {
        return build(HttpStatus.BAD_REQUEST, "invalid or missing parameter", r);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorDto> down(Exception e, WebRequest r) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "database unavailable", r);
    }

    private ResponseEntity<ErrorDto> build(HttpStatus s, String msg, WebRequest r) {
        ErrorDto body = new ErrorDto(
            Instant.now(), s.value(), s.getReasonPhrase(), msg,
            r.getDescription(false).replace("uri=", ""));
        return ResponseEntity.status(s).body(body);
    }
}
