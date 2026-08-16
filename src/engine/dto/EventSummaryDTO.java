package engine.dto;

import java.util.List;

public record EventSummaryDTO(
        int id,
        String name,
        String description,
        String status, // למשל: "OPEN", "CLOSED", "NOT_STARTED"
        int commission,
        String commissionType,
        List<String> options
) {}
