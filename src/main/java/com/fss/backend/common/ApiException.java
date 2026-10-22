package com.fss.backend.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(String message) {
        this(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }

    public ApiException(ErrorCode code, String message) {
        this(HttpStatus.BAD_REQUEST, code, message);
    }

    public ApiException(HttpStatus status, String message) {
        this(status, ErrorCode.forStatus(status), message);
    }

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** The resource addressed by the request path does not exist (or is not visible to the caller). */
    public static ApiException notFound(ErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** The request clashes with existing data, e.g. a unique slug or code that is already taken. */
    public static ApiException conflict(ErrorCode code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }
}
