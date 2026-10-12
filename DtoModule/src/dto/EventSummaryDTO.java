package dto;

import java.util.List;

public record EventSummaryDTO(
        String name,
        String description,
        String status, // למשל: "OPEN", "CLOSED", "NOT_STARTED"
        int commission,
        String commissionType,
        List<String> options,
        String tradingMethodType,
        String marketMaker, // the user who uploaded the event's XML file
        double accountBalance // money in the event's own account (0 before it starts and after it closes)
) {}
