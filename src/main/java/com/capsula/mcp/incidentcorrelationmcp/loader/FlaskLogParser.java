package com.capsula.mcp.incidentcorrelationmcp.loader;

import com.capsula.mcp.incidentcorrelationmcp.model.Severity;
import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlaskLogParser implements LogParser {
    private static final Pattern PATTERN = Pattern.compile(
            "^ts=(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}),\\d+\\s+" +
            "level=([A-Z]+)\\s+" +
            "line=\\d+\\s+" +
            "msg=\"(.*)\"$");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean supports(String line) {
        return line.startsWith("ts=");
    }

    @Override
    public Optional<SystemEvent> parse(String line, String service) {
        Matcher matcher = PATTERN.matcher(line);
        if(!matcher.find()) {
            System.err.println("Failed to parse line: " + line);
            return Optional.empty();
        }
        LocalDateTime time = LocalDateTime.parse(matcher.group(1), FORMATTER);
        String level = matcher.group(2);
        String message = matcher.group(3);

        Severity severity = switch (level) {
            case "ERROR" -> Severity.ERROR;
            case "WARNING", "WARN" -> Severity.WARNING;
            default -> Severity.INFO;
        };
        return Optional.of(
                new SystemEvent(
                        UUID.randomUUID().toString(),
                        service,
                        time,
                        severity,
                        message
                )
        );
    }
}
