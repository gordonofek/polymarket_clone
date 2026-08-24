package internals;

public record TradeRequest(ActionType action, String optionName
        , int amount, double limitPrice) {}
