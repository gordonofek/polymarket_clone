package internals;

import dto.BalanceChangeType;

// one change to a user's balance, as shown in their balance history
public record BalanceChange(
        BalanceChangeType type,
        double amount,       // positive = money in, negative = money out
        double balanceAfter,
        long timestamp,      // epoch millis
        String details
) {}