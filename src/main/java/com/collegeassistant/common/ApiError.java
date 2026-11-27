package com.collegeassistant.common;

import java.time.Instant;

/** Single error format for every JSON API response. */
public record ApiError(Instant timestamp, int status, String code, String message) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message);
    }
}
