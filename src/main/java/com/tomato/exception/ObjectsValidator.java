package com.tomato.exception;

public class ObjectsValidator {

    private ObjectsValidator() {
    }

    public static void mustNotNull(Object object, ErrorCode errorCode) {
        if (object == null) {
            throw new BusinessException(errorCode);
        }
    }

    public static void mustNotNull(Object object, ErrorCode errorCode, String customMessage) {
        if (object == null) {
            throw new BusinessException(errorCode, customMessage);
        }
    }

    public static void mustNull(Object object, ErrorCode errorCode) {
        if (object != null) {
            throw new BusinessException(errorCode);
        }
    }

    public static void mustNull(Object object, ErrorCode errorCode, String customMessage) {
        if (object != null) {
            throw new BusinessException(errorCode, customMessage);
        }
    }
}
