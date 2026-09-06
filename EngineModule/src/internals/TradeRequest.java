package internals;

public class TradeRequest {
    private ActionType action;
    private String optionName;
    private int amount;
    private double limitPrice;
    private String username;

    public TradeRequest(ActionType action, String optionName, int amount, double limitPrice, String username) {
        this.action = action;
        this.optionName = optionName;
        this.amount = amount;
        this.limitPrice = limitPrice;
        this.username = username;
    }

    // --- Getters ---
    public ActionType getAction() { return action; }
    public String getOptionName() { return optionName; }
    public int getAmount() { return amount; }
    public double getLimitPrice() { return limitPrice; }
    public String getUsername() { return username; }

    // --- Setters ---
    // זה ה-Setter שחסר לך כדי לעדכן את כמות המניות אחרי עסקה חלקית!
    public void setAmount(int amount) {
        this.amount = amount;
    }
}