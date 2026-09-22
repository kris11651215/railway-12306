package com.railway.ai.tool;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class AiToolArguments {

    private AiToolArguments() {
    }

    public static String text(java.util.Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static LocalDate date(java.util.Map<String, Object> arguments, String key) {
        String value = text(arguments, key);
        if (value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
