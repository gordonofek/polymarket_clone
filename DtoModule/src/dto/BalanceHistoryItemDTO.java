package dto;

public record BalanceHistoryItemDTO(
        BalanceChangeType type,
        double amount,       // positive = money in, negative = money out
        double balanceAfter,
        long timestamp,      // epoch millis, formatted by the client
        String details       // e.g. "Bought 10 'Yes' in 'Rain' @ $0.60"
) {}