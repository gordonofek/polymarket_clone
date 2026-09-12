package internals;

import java.io.Serializable;

public record TradeResult(String orderId, String username,
                          boolean isSuccess, String optionName,
                          ActionType action, int sharesProcessed,
                          double totalCost, double commissionPaid,
                          String message) implements Serializable {}
