package com.capsula.mcp.incidentcorrelationmcp.service;

import com.capsula.mcp.incidentcorrelationmcp.model.CorrelationResult;
import com.capsula.mcp.incidentcorrelationmcp.model.Severity;
import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;
import com.capsula.mcp.incidentcorrelationmcp.repository.EventRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CorrelationService {
    private final EventRepository eventRepository;

    public CorrelationService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<CorrelationResult> findCorrelatedEvent(String eventId, int windowMinutes) {
        SystemEvent sourceEvent = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        var candidates = eventRepository.findEventsAround(sourceEvent.timestamp(), windowMinutes, windowMinutes);

        return candidates.stream()
                .filter(event -> !event.id().equals(sourceEvent.id()))
                .map(event -> calculateCorrelation(sourceEvent, event))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(CorrelationResult::score).reversed())
                .toList();
    }

    private CorrelationResult calculateCorrelation(SystemEvent sourceEvent, SystemEvent candidate) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if(sourceEvent.service().equalsIgnoreCase(candidate.service())) {
            score += 40;
            reasons.add("Same service: " + sourceEvent.service());
        }

        long seconds = Math.abs(Duration.between(sourceEvent.timestamp(), candidate.timestamp()).toSeconds());
        if (seconds <= 30) {
            score += 30;
            reasons.add("Ocurred within 30 seconds");
        } else if (seconds <= 120) {
            score += 20;
            reasons.add("Ocurred within 2 minute");
        }

        if(sourceEvent.severity() == Severity.ERROR) {
            score += 20;
            reasons.add("Candidate event has severity ERROR");
        } else if(sourceEvent.severity() == Severity.WARNING) {
            score += 10;
            reasons.add("Candidate event has severity WARNING");
        }

        if(candidate.message().toLowerCase().contains(sourceEvent.message().toLowerCase())) {
            score += 20;
            reasons.add("Candidate event message contains source event message");
        }

        if(sourceEvent.message().toLowerCase().contains(candidate.message().toLowerCase())) {
            score += 20;
            reasons.add("Source event message contains candidate event message");
        }

        return new CorrelationResult(candidate, score, reasons);
    }


}
