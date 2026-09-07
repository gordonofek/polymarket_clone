package engine.dto;

public record PortfolioItemDTO(
        String eventName,
        String optionName,
        int shares
) {}