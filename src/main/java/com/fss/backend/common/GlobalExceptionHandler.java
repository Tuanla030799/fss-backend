package com.fss.backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns every exception into the error envelope {@code {success:false, code, message, data:null[, errors]}} with a
 * meaningful status: 400 for malformed requests, 404 for missing resources, 409 for conflicts, 5xx for server faults.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
        log.warn("API exception {}: {}", ex.code(), ex.getMessage());
        return error(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
        // Unique indexes back up the service-level checks when two requests race.
        log.warn("Duplicate key: {}", ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_VALUE, "Duplicate value already exists");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE, "File is too large");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseUnavailable(DataAccessResourceFailureException ex) {
        log.error("Database unavailable", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE,
                "Hệ thống đang tạm gián đoạn, vui lòng thử lại sau.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorResponse.FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(fieldError(error.getField(), error)));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errors.add(fieldError(error.getObjectName(), error)));
        return validationError(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ApiErrorResponse.FieldError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getAllValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors && parameterErrors.hasFieldErrors()) {
                for (FieldError error : parameterErrors.getFieldErrors()) errors.add(fieldError(error.getField(), error));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(error -> errors.add(fieldError(name, error)));
            }
        }
        return validationError(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationError(List.of(new ApiErrorResponse.FieldError(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationError(List.of(new ApiErrorResponse.FieldError(parameterName(ex.getParameter()), "has an invalid value")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.warn("Invalid request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST_BODY, "Invalid request body");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            // Spring MVC request errors: unknown route (404), unsupported method (405) or media type (415), ...
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("Request rejected with {}: {}", status.value(), ex.getMessage());
            String detail = errorResponse.getBody().getDetail();
            return error(status, ErrorCode.forStatus(status), detail == null ? "Request failed" : detail);
        }
        log.error("Unexpected exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau.");
    }

    private ResponseEntity<ApiErrorResponse> validationError(List<ApiErrorResponse.FieldError> errors) {
        // The message keeps the former "<field> <problem>" text of the first error; errors lists them all.
        String message = errors.stream().findFirst().map(error -> error.field() + " " + error.message()).orElse("Validation failed");
        log.warn("Validation failed: {}", message);
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, message, errors));
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatusCode status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }

    private ApiErrorResponse.FieldError fieldError(String field, MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return new ApiErrorResponse.FieldError(field, message == null ? "is invalid" : message);
    }

    /** The name the client used: the {@code @RequestParam}/{@code @PathVariable} name, else the Java parameter name. */
    private String parameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            if (!requestParam.name().isBlank()) return requestParam.name();
            if (!requestParam.value().isBlank()) return requestParam.value();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            if (!pathVariable.name().isBlank()) return pathVariable.name();
            if (!pathVariable.value().isBlank()) return pathVariable.value();
        }
        String name = parameter.getParameterName();
        return name == null ? "parameter" : name;
    }
}
