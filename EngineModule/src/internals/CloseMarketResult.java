package internals;

public record CloseMarketResult(
        String winningOption,
        double totalPayout,
        double commissionTaken,
        double netPayoutToWinners,
        double leftoverSubsidy
) {}
