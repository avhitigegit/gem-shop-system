package lk.nuvi.gemshop.common.error;

import org.springframework.http.HttpStatus;

/** Throw this from any service to return an ApiError with the given status, code and message. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final java.util.List<ApiError.FieldError> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, java.util.List.of());
    }

    public ApiException(HttpStatus status, String code, String message, java.util.List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    /** Error that marks one form field red, e.g. field("newPassword", "At least 8 characters…"). */
    public static ApiException field(HttpStatus status, String code, String message, String field, String fieldMessage) {
        return new ApiException(status, code, message, java.util.List.of(new ApiError.FieldError(field, fieldMessage)));
    }

    public java.util.List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
