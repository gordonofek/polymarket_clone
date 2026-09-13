package engine.dto;

public record PendingRequestDTO(
        String username,
        int amount,
        double price,
        String type
) {}