package com.tomato.exception;

public enum ErrorCode {
    SUCCESS(0, "Success"),
    ERROR_400_4000(4000, "Bad request"),
    ERROR_400_VALIDATION(4001, "Validation failed"),
    ERROR_404_2001(2001, "User not found"),
    ERROR_409_2002(2002, "Username already taken"),
    ERROR_409_2003(2003, "Email already in use"),
    ERROR_500_5000(5000, "Internal server error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
