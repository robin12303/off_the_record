package dev.backend.util;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TimeUtil {
    private TimeUtil() {}

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("'['yyyy-MM-dd HH:mm:ss']'");

    public static LocalDateTime parseTs(String s) {
        try {
            return LocalDateTime.parse(s, FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Bad timestamp: " + s, e);
        }
    }
}