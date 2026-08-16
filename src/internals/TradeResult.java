package internals;

import java.io.Serializable;

public record TradeResult(boolean isSuccess, String optionName,
                          int sharesProcessed, double totalCost,
                          double commissionPaid, String message)
                          implements Serializable {}
