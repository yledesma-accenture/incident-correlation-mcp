package com.capsula.mcp.incidentcorrelationmcp;

import com.capsula.mcp.incidentcorrelationmcp.loader.LogLoader;
import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;
import com.capsula.mcp.incidentcorrelationmcp.repository.EventRepository;
import com.capsula.mcp.incidentcorrelationmcp.service.CorrelationService;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IncidentMcpServer {

    public static void main(String[] args) {
        /* 
         * Cargar logs reales
         */

        LogLoader loader = new LogLoader();

        List<SystemEvent> events = new ArrayList<>();
        events.addAll(loader.load("logs/bug-service.log", "bug-service"));
        events.addAll(loader.load("logs/db.log", "database"));
        events.addAll(loader.load("logs/main-app.log", "main-app"));
        events.addAll(loader.load("logs/plant-service.log", "plant-service"));
        events.addAll(loader.load("logs/simulation-service.log", "simulation-service"));
        events.addAll(loader.load("logs/user-service.log", "user-service"));
        events.addAll(loader.load("logs/websocket-service.log", "websocket-service"));


        /* 
         * Crear repositorio de eventos
         */
        EventRepository eventRepository = new EventRepository(events);

        System.err.println("Events loaded: " + eventRepository.findAll().size());

        eventRepository.findAll().stream().limit(10)
                .forEach(event -> System.err.println(event.timestamp() + " | " + event.service() + " | " + event.severity() + " | " + event.message()));

        CorrelationService correlationService = new CorrelationService(eventRepository);

        /* 
         * Crear transporte MCP
         */
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        /* 
         * Schema tools
         */

        Map<String, Object> getEventsAroundSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "timestamp", Map.of(
                                "type", "string",
                                "description",
                                "Incident timestamp in ISO 8601 format"
                        ),
                        "minutesBefore", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "description",
                                "Minutes to search before the timestamp"
                        ),
                        "minutesAfter", Map.of(
                                "type", "integer",
                                "minimum", 0,
                                "description",
                                "Minutes to search after the timestamp"
                        )
                ),

                "required", List.of(
                        "timestamp",
                        "minutesBefore",
                        "minutesAfter"
                ),
                "additionalProperties", false
        );

        Map<String, Object> searchEventsSchema =
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "service", Map.of(
                                        "type", "string",
                                        "description", "Filter by service name. Example: 'user-service', 'database', 'main-app'"
                                ),
                                "severity", Map.of(
                                        "type", "string",
                                        "description", "Filter by severity level. Example: 'INFO', 'WARN', 'ERROR'"
                                ),
                                "text", Map.of(
                                        "type", "string",
                                        "description", "Text to search in event messages"
                                )
                        ),
                        "additionalProperties", false
                );

        Map<String, Object> correlationSchema =
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "eventId", Map.of(
                                        "type", "string",
                                        "description", "ID of the event to find correlations for"
                                ),
                                "windowMinutes", Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "description", "Time window in minutes to search for correlated events"
                                )
                        ),
                        "required", List.of(
                                "eventId",
                                "windowMinutes"
                        ),
                        "additionalProperties", false
                );

        /* 
         * Definir tools
         */

        var getEventAroundTool = McpServerFeatures.SyncToolSpecification
                .builder()
                .tool(McpSchema.Tool
                                .builder("get_events_around", getEventsAroundSchema)
                                .description("Returns system events that ocurred within a time window around a given timestamp")
                                .build()
                        )
                .callHandler((exchange, request) -> {
                            String timestamp = (String) request.arguments().get("timestamp");
                            int minutesBefore = ((Number) request.arguments().get("minutesBefore")).intValue();
                            int minutesAfter = ((Number) request.arguments().get("minutesAfter")).intValue();

                            LocalDateTime dateTime = LocalDateTime.parse(timestamp);

                            var foundEvents = eventRepository.findEventsAround(dateTime, minutesBefore, minutesAfter);

                            String result;

                            if(foundEvents.isEmpty()) {
                                result = "No events found between " + dateTime.minusMinutes(minutesBefore) + " and " + dateTime.plusMinutes(minutesAfter);
                            } else {
                                result = "Events found: " +
                                        foundEvents.size() + "\n" +
                                        "Time window: " + dateTime.minusMinutes(minutesBefore) + " - " + dateTime.plusMinutes(minutesAfter) + "\n" +
                                        "Events:\n" +
                                        foundEvents.stream()
                                        .map(event -> "%s | %s | %s | %s | %s".formatted(
                                                event.id(),
                                                event.timestamp(),
                                                event.service(),
                                                event.severity(),
                                                event.message()
                                        ))
                                        .collect(Collectors.joining("\n"));
                            }

                            return McpSchema.CallToolResult.builder()
                                    .content(
                                            List.of(
                                                    McpSchema.TextContent.builder(result).build()
                                            )
                                    ).build();
                        })
                .build();

        var searchEventsTool = McpServerFeatures.SyncToolSpecification
                .builder()
                .tool(McpSchema.Tool
                        .builder("search_events", searchEventsSchema)
                        .description("Search system events by service, severity and text")
                        .build()
                )
                .callHandler((exchange, request) -> {
                    String service = (String) request.arguments().get("service");
                    String severity = (String) request.arguments().get("severity");
                    String text = (String) request.arguments().get("text");

                    var foundEvents = eventRepository.search(service, severity, text);

                    String result = foundEvents.isEmpty()
                            ? "No events found"
                            : foundEvents.stream()
                            .map(event -> "%s | %s | %s | %s | %s".formatted(
                                                event.id(),
                                                event.timestamp(),
                                                event.service(),
                                                event.severity(),
                                                event.message()
                                        ))
                                        .collect(Collectors.joining("\n"));

                    return McpSchema.CallToolResult.builder()
                            .content(
                                    List.of(
                                            McpSchema.TextContent.builder(result).build()
                                    )
                            ).build();
                })
                .build();

        var findCorrelatedEventsTool = McpServerFeatures.SyncToolSpecification
                .builder()
                .tool(McpSchema.Tool
                        .builder("find_correlated_events", correlationSchema)
                        .description("Find correlated events for a given event ID within a time window")
                        .build()
                )
                .callHandler((exchange, request) -> {
                    String eventId = (String) request.arguments().get("eventId");
                    int windowMinutes = ((Number) request.arguments().get("windowMinutes")).intValue();

                    try {
                        var correlatedResults = correlationService.findCorrelatedEvent(eventId, windowMinutes);

                        String result;

                        if (correlatedResults.isEmpty()) {
                            result = "No correlated events found for event ID: " + eventId;
                        } else {
                            result = correlatedResults.stream()
                                    .map(correlationResult -> {
                                        var event = correlationResult.event();

                                        return """
                                                Score: %d
                                                Event ID: %s
                                                Timestamp: %s
                                                Service: %s
                                                Severity: %s
                                                Message: %s
                                                Reasons: 
                                                - %s
                                                """.formatted(
                                                correlationResult.score(),
                                                event.id(),
                                                event.timestamp(),
                                                event.service(),
                                                event.severity(),
                                                event.message(),
                                                String.join("\n- ", correlationResult.reasons())
                                        );
                                    })
                                    .collect(Collectors.joining("\n----------------------------\n"));
                        }

                            return McpSchema.CallToolResult.builder()
                                    .content(
                                            List.of(
                                                    McpSchema.TextContent.builder(result).build()
                                            )
                                    ).build();


                    } catch (IllegalArgumentException e) {
                        return McpSchema.CallToolResult.builder()
                                .isError(true)
                                .content(
                                        List.of(
                                                McpSchema.TextContent.builder(e.getMessage()).build()
                                        )
                                ).build();
                    }
                })
                .build();


        /* 
         * Crear servidor MCP y registrar tools
         */

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("incident-correlation-mcp", "1.0.0")
                .capabilities(
                        ServerCapabilities.builder()
                                .tools(true)
                                .build())
                .tools(getEventAroundTool, searchEventsTool, findCorrelatedEventsTool)
                .build();


        System.err.println("Incident Correlation MCP server started.");

        /* 
         * Cierre ordenado
         */
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    }

}
