package lk.nuvi.gemshop.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception into the one {@link ApiError} format. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CHECK_FIELDS = "Please check the highlighted fields.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest req) {
        return build(e.getStatus(), e.getCode(), e.getMessage(), req, e.getFieldErrors());
    }

    /** A role check (@PreAuthorize) said no. */
    @ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
    ResponseEntity<ApiError> denied(Exception e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Your login cannot do this.", req, List.of());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> accessDenied(Exception e, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Your login cannot do this.", req, List.of());
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<ApiError> notSignedIn(Exception e, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in.", req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, CHECK_FIELDS, req, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalidParams(HandlerMethodValidationException e, HttpServletRequest req) {
        List<ApiError.FieldError> fields = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new ApiError.FieldError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, CHECK_FIELDS, req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e, HttpServletRequest req) {
        List<ApiError.FieldError> fields = e.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(String.valueOf(v.getPropertyPath()), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, CHECK_FIELDS, req, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> badRequest(Exception e, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "The request is not valid.", req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException e, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Not found.", req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, "This action is not allowed here.", req, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus s = HttpStatus.valueOf(e.getStatusCode().value());
        return build(s, s.name(), e.getReason() != null ? e.getReason() : s.getReasonPhrase(), req, List.of());
    }

    /** Two people saved the same record at the same moment (e.g. the owner approving on phone and computer). */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> changedMeanwhile(Exception e, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "CHANGED_MEANWHILE", "Someone else changed this just now. Please reload and try again.",
                req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
        log.error("Unexpected error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Something went wrong. Please try again.", req, List.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                                  HttpServletRequest req, List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message, req.getRequestURI(), fields));
    }
}
