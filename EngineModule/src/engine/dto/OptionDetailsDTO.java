package engine.dto;

public record OptionDetailsDTO(
        String optionName,
        double currentPrice,
        int sharesBought
) {}
