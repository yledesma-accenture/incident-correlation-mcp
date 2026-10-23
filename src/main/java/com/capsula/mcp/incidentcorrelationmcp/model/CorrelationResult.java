package com.capsula.mcp.incidentcorrelationmcp.model;

import java.util.List;

public record CorrelationResult(
        SystemEvent event,
        int score,
        List<String> reasons
) {
}
