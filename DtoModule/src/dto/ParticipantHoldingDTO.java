package dto;

import java.util.Map;

public record ParticipantHoldingDTO(
        String username,
        // optionName -> amount of shares
        Map<String, Integer> shares
) {}