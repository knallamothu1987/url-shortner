package com.shortener.api;

import com.shortener.application.InvalidAliasException;
import com.shortener.application.InvalidUrlException;
import com.shortener.application.ServiceTemporarilyUnavailableException;
import com.shortener.application.ShortCodeAlreadyExistsException;
import com.shortener.application.UrlExpiredException;
import com.shortener.application.UrlNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidUrlException.class, InvalidAliasException.class, IllegalArgumentException.class})
    ProblemDetail badRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(ShortCodeAlreadyExistsException.class)
    ProblemDetail conflict(ShortCodeAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(UrlNotFoundException.class)
    ProblemDetail notFound(UrlNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UrlExpiredException.class)
    ProblemDetail gone(UrlExpiredException exception) {
        return problem(HttpStatus.GONE, exception.getMessage());
    }

    @ExceptionHandler(ServiceTemporarilyUnavailableException.class)
    ProblemDetail unavailable(ServiceTemporarilyUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    ProblemDetail validation(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail constraintViolation(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "The request conflicts with persisted data");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
