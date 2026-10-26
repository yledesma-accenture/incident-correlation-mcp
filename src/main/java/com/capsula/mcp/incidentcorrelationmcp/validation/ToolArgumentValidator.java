package com.capsula.mcp.incidentcorrelationmcp.validation;

import java.util.Map;

public class ToolArgumentValidator {

    public static String requiereString(Map<String,Object> argument, String key) {
        Object value = argument.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Parameter " + key + "cannot be empty");
            }
            return trimmed;
        }
        throw new IllegalArgumentException("Parameter '" + key + "'must be a string");
    }

    public static Integer requiereInteger(Map<String,Object> argument, String key) {
        Object value = argument.get(key);
        switch (value) {
            case null -> throw new IllegalArgumentException("Missing required argument: " + key);
            case Number number -> {
                return number.intValue();
            }
            case String stringValue -> {
                try {
                    return Integer.parseInt(stringValue.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Parameter '" + key + "' must be an integer", e);
                }
            }
            default -> {
            }
        }
        throw new IllegalArgumentException("Parameter '" + key + "'must be a string");
    }

    public static String optionalString(Map<String,Object> argument, String key) {
        Object value = argument.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        throw new IllegalArgumentException("Parameter '" + key + "'must be a string");
    }
}
