package dto;

import java.util.List;

public record EventSummaryDTO(
        String name,
        String description,
        String status, // למשל: "OPEN", "CLOSED", "NOT_STARTED"
        int commission,
        String commissionType,
        List<String> options,
        String tradingMethodType
) {}
