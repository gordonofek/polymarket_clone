package dto;


public record TradeRecordDTO(
        ActionType action, // תומך גם במכירה בעתיד
        String optionName,
        int amount,
        double pricePaid
) {}