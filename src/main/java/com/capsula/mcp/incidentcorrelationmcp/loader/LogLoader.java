package com.capsula.mcp.incidentcorrelationmcp.loader;

import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LogLoader {
    private final List<LogParser> parsers =
            List.of(new FlaskLogParser(), new PostgresLogParser());

    public List<SystemEvent> load(String resource, String service) {
        List<SystemEvent> events = new ArrayList<>();

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource);
        if (inputStream == null) {
            throw new IllegalArgumentException("Log file not found: " + resource);
        }

        try {
            byte[] content = readAllBytes(inputStream);
            Charset charset = detectCharset(content);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), charset))) {
                String line;
                while((line = reader.readLine()) != null) {
                    if(line.isBlank()) {
                        continue;
                    }
                    for (LogParser parser : parsers) {
                        if (!parser.supports(line)) {
                            continue;
                        }
                        parser.parse(line, service).ifPresent(events::add);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading log file: " + resource, e);
        }
        return events;
    }

    private Charset detectCharset(byte[] content) {
        // Check for UTF-16 LE BOM
        if (content.length >= 2 && content[0] == (byte) 0xFF && content[1] == (byte) 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        // Check for UTF-16 BE BOM
        if (content.length >= 2 && content[0] == (byte) 0xFE && content[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        // Check for UTF-8 BOM
        if (content.length >= 3 && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // Default to UTF-8 if no BOM is found
        return StandardCharsets.UTF_8;
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        var output = new ByteArrayOutputStream();
        while((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
