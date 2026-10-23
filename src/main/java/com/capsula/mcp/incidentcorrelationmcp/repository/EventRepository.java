package com.capsula.mcp.incidentcorrelationmcp.repository;

import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Locale.filter;
import static java.util.stream.Collectors.toList;

public class EventRepository {
    private final List<SystemEvent> events;

    public EventRepository(List<SystemEvent> events) {
        this.events = new ArrayList<>(events);

        this.events.sort(
                (a, b) -> a.timestamp().compareTo(b.timestamp()));
    }

    public List<SystemEvent> findAll() {
        return List.copyOf(events);
    }

    public Optional<SystemEvent> findById(String id) {
        return events.stream()
                .filter(event -> event.id().equals(id)).findFirst();
    }

    public List<SystemEvent> findEventsAround(LocalDateTime timestamp, int minutesBefore, int minutesAfter) {
        LocalDateTime from = timestamp.minusMinutes(minutesBefore);
        LocalDateTime to = timestamp.plusMinutes(minutesAfter);

        return events.stream()
                .filter(event -> !event.timestamp().isBefore(from) && !event.timestamp().isAfter(to))
                .toList();
    }

    public List<SystemEvent> search(String service, String severity, String text) {
        return events.stream()
        .filter(event -> service == null
                || service.isBlank()
                || event.service().equalsIgnoreCase(service)
        )
        .filter(event -> severity == null
                || severity.isBlank()
                || event.severity().name().equalsIgnoreCase(severity)
        )
        .filter(event -> text == null
                || text.isBlank()
                || event.message().toLowerCase().contains(text.toLowerCase())
        ).toList();
    }




}
