package dto;

public record BalanceHistoryItemDTO(
        String action, // "DEPOSIT" or "WITHDRAW"
        double amount,
        double balanceAfter,
        long timestamp // epoch millis, formatted by the client
) {}