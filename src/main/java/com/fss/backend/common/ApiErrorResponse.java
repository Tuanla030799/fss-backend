package com.fss.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Body of every error response: the usual envelope ({@code success=false}, {@code message}, {@code data=null}) plus a
 * stable {@link ErrorCode} in {@code code} and, for validation failures, one entry per invalid field in {@code errors}.
 */
@JsonPropertyOrder({"success", "code", "message", "data", "errors"})
public record ApiErrorResponse(boolean success, String code, String message, Object data,
                               @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> errors) {

    public record FieldError(String field, String message) {}

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return of(code, message, List.of());
    }

    public static ApiErrorResponse of(ErrorCode code, String message, List<FieldError> errors) {
        return new ApiErrorResponse(false, code.name(), message, null, errors);
    }
}
