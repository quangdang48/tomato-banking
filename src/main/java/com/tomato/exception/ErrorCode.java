package com.tomato.exception;

public enum ErrorCode {
    SUCCESS(0, "Success"),
    ERROR_400_4000(4000, "Bad request"),
    ERROR_400_VALIDATION(4001, "Validation failed"),
    ERROR_401_2200(2200, "Invalid username or password"),
    ERROR_401_2201(2201, "Invalid or expired token"),
    ERROR_404_2001(2001, "User not found"),
    ERROR_409_2002(2002, "Username already taken"),
    ERROR_409_2003(2003, "Email already in use"),
    ERROR_404_2300(2300, "Onboarding profile not found"),
    ERROR_409_2301(2301, "Onboarding profile already exists"),
    ERROR_403_2302(2302, "Onboarding is not approved"),
    ERROR_400_2303(2303, "Required KYC data is missing"),
    ERROR_400_2304(2304, "Required KYB data is missing"),
    ERROR_400_2305(2305, "Required document is missing"),
    ERROR_409_2306(2306, "Invalid onboarding status transition"),
    ERROR_400_2307(2307, "Beneficial owner is required"),
    ERROR_409_2308(2308, "Onboarding has been rejected"),
    ERROR_403_2309(2309, "Reviewer access required"),
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
