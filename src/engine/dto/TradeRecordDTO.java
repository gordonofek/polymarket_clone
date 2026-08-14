package engine.dto;

import internals.ActionType;

public record TradeRecordDTO(
        ActionType action, // תומך גם במכירה בעתיד
        String optionName,
        int amount,
        double pricePaid
) {}