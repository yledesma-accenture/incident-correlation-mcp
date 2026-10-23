package com.capsula.mcp.incidentcorrelationmcp.loader;

import com.capsula.mcp.incidentcorrelationmcp.model.Severity;
import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LogLoader {
    private final List<LogParser> parsers =
            List.of(new FlaskLogParser(), new PostgresLogParser());

    public List<SystemEvent> load(String resource, String service) {
        List<SystemEvent> events = new ArrayList<>();

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource);
        if (inputStream == null) {
            throw new IllegalArgumentException("Log file not found: " + resource);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_16))){
            String line;
            while((line = reader.readLine()) != null) {
                for (LogParser parser : parsers) {
                    if (!parser.supports(line)) {
                        continue;
                    }
                    parser.parse(line, service).ifPresent(events::add);
                    break;
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error reading log file: " + resource, e);
        }
        return events;
    }

    private SystemEvent createEvent(String service, LocalDateTime timestamp, String message) {
        return new SystemEvent(
                UUID.randomUUID().toString(),
                service,
                timestamp,
                detectSeverity(message),
                message);
    }

    private Severity detectSeverity(String message) {
        String normalized = message.toLowerCase();
        if (normalized.contains("fatal") || normalized.contains("error") || normalized.contains("exception")
                || normalized.contains("traceback") || normalized.contains("connection refused")) {
            return Severity.ERROR;
        }
        if (normalized.contains("warning") || normalized.contains("warn")) {
            return Severity.WARNING;
        }

        return Severity.INFO;
    }
}
