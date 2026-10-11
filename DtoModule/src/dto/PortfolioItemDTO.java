package dto;

public record PortfolioItemDTO(
        String eventName,
        String optionName,
        int shares,
        String status,
        double profitLoss
) {}