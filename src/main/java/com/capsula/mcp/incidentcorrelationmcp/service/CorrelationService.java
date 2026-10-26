package com.capsula.mcp.incidentcorrelationmcp.service;

import com.capsula.mcp.incidentcorrelationmcp.model.CorrelationResult;
import com.capsula.mcp.incidentcorrelationmcp.model.Severity;
import com.capsula.mcp.incidentcorrelationmcp.model.SystemEvent;
import com.capsula.mcp.incidentcorrelationmcp.repository.EventRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

        // El servicio compartido recibe el mayor peso porque vincula los eventos al mismo componente.
        if (sourceEvent.service().equalsIgnoreCase(candidate.service())) {
            score += 40;
            reasons.add("Same service: " + sourceEvent.service());
        }

        long seconds = Math.abs(Duration.between(sourceEvent.timestamp(), candidate.timestamp()).toSeconds());

        // Una diferencia de hasta 30 segundos es una señal temporal más fuerte que una de hasta 2 minutos
        if (seconds <= 30) {
            score += 30;
            reasons.add("Occurred within 30 seconds");
        } else if (seconds <= 120) {
            score += 20;
            reasons.add("Occurred within 2 minutes");
        }

        // La severidad describe la relevancia del candidato: ERROR pesa mas que WARNING
        if(candidate.severity() == Severity.ERROR) {
            score += 20;
            reasons.add("Candidate severity is ERROR");
        } else if (candidate.severity() == Severity.WARNING) {
            score += 10;
            reasons.add("Candidate severity is WARNING");
        }

        String sourceMessage = sourceEvent.message().trim().toLowerCase(Locale.ROOT);
        String candidateMessage = candidate.message().trim().toLowerCase(Locale.ROOT);
        // El texto coincidente suma una sola vez, aunque una de las frases contenga a la otra.
        if(!sourceMessage.isEmpty()
            && !candidateMessage.isEmpty()
            && (candidateMessage.contains(sourceMessage) || sourceMessage.contains(candidateMessage))) {
            score += 20;
            reasons.add("Event messages overlap");
        }

        return new CorrelationResult(candidate, score, reasons);
    }


}
