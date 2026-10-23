package com.capsula.mcp.incidentcorrelationmcp.loader;

import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;

import java.util.Optional;

public interface LogParser {
    boolean supports(String line);

    Optional<SystemEvent> parse(String line, String service);
}
