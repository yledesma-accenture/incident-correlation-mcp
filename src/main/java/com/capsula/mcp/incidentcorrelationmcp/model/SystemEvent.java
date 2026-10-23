package com.capsula.mcp.incidentcorrelationmcp.model;

import java.time.LocalDateTime;

public record SystemEvent(String id,
                          String service,
                          LocalDateTime timestamp,
                          Severity severity,
                          String message) {
}
