package engine.dto;

import java.util.List;

public record EventsDeltaDTO(
        int serverVersion,
        List<EventSummaryDTO> updatedEvents // רשימה של אירועים שהתעדכנו
) {}