package com.cloudzone.shopbackend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates the exceptions a request can raise into the uniform {@link ApiError} body.
 *
 * <p>The security filter chain handles authentication failures before the controller layer
 * ever runs, so this advice only sees: malformed input, business-rule violations, and
 * genuinely unexpected errors. Everything is logged once here; controllers stay free of
 * try/catch boilerplate.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Business-rule violations — the only place a 4xx code is chosen deliberately. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getCode(), ex.getMessage(),
                ex.getStatus().value(), request.getRequestURI());
        if (ex.getStatus().is5xxServerError()) {
            log.error("{} on {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        } else {
            log.debug("{} on {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** Bean-validation failures: collapse every field error into one readable message. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        String message = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining("; "));
        ApiError body = ApiError.of("validation_failed", message,
                HttpStatus.BAD_REQUEST.value(), request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /** Malformed JSON, wrong types in the body, unparseable payloads. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ApiError body = ApiError.of("malformed_body", "Request body is missing or malformed",
                HttpStatus.BAD_REQUEST.value(), request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * The DB is the final guard on unique email. The service pre-checks for a friendlier
     * message, but a concurrent insert can still land here; map it back to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        ApiError body = ApiError.of("conflict", "Resource already exists",
                HttpStatus.CONFLICT.value(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** A valid token lacked the authority for the route — distinct from "no/bad token". */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ApiError body = ApiError.of("forbidden", "You do not have permission to do that",
                HttpStatus.FORBIDDEN.value(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Framework-raised protocol errors — unknown path ({@code NoResourceFoundException}),
     * unsupported method or media type. Without this they fall into the catch-all below
     * and surface as 500s, blaming the server for what is a client mistake.
     *
     * <p>Types are listed concretely rather than matching the {@code ErrorResponse}
     * interface: the resolver then never has to compare an interface against
     * {@link Exception} to decide which advice method is "more specific".
     */
    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class,
    })
    public ResponseEntity<ApiError> handleProtocolError(
            Exception ex, HttpServletRequest request) {
        if (!(ex instanceof ErrorResponse errorResponse)) {
            // Every listed type implements ErrorResponse; fall back rather than throw.
            log.warn("Non-ErrorResponse {} reached the protocol handler", ex.getClass().getName());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of(
                    "request_error", "The request could not be processed",
                    HttpStatus.BAD_REQUEST.value(), request.getRequestURI()));
        }
        int status = errorResponse.getStatusCode().value();
        String title = errorResponse.getBody().getTitle();
        log.debug("protocol error {} on {}: {}", status, request.getRequestURI(), title);
        ApiError body = ApiError.of(protocolCode(status), title, status, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Upload over the servlet multipart limit — a client mistake, not a server fault. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.debug("oversized upload on {}: {}", request.getRequestURI(), ex.getMessage());
        ApiError body = ApiError.of("media_too_large", "File exceeds the upload size limit",
                HttpStatus.PAYLOAD_TOO_LARGE.value(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    /** Bad query/path value binding — e.g. {@code ?size=abc} — is the client's mistake. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> required = ex.getRequiredType();
        String message = "Invalid value '" + ex.getValue() + "' for parameter '"
                + ex.getName() + "'"
                + (required == null ? "" : "; expected " + required.getSimpleName());
        ApiError body = ApiError.of("invalid_parameter", message,
                HttpStatus.BAD_REQUEST.value(), request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    private static String protocolCode(int status) {
        return switch (status) {
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 406 -> "not_acceptable";
            case 415 -> "unsupported_media_type";
            default -> "request_error";
        };
    }

    /** Catch-all: log the detail, but never let a stack trace reach the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        ApiError body = ApiError.of("internal_error", "Something went wrong on our side",
                HttpStatus.INTERNAL_SERVER_ERROR.value(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
