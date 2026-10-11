package internals;

// one deposit or withdrawal in a user's balance history
public record BalanceChange(
        boolean isDeposit,
        double amount,
        double balanceAfter,
        long timestamp // epoch millis
) {}