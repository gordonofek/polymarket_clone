package engine.dto;

public record EventSummaryDTO(
        int id,
        String name,
        String description,
        String status // למשל: "OPEN", "CLOSED", "NOT_STARTED"
) {}
