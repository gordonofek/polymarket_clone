package internals;

public record TradeResult(boolean isSuccess, String optionName,
                          int sharesProcessed, double totalCost,
                          double commissionPaid, String message) {}
