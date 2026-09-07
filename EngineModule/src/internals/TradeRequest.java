package internals;

import java.util.UUID;

public class TradeRequest {
    private String orderId;
    private ActionType action;
    private String optionName;
    private int amount;
    private double limitPrice;
    private String username;

    public TradeRequest(ActionType action, String optionName, int amount, double limitPrice, String username) {
        this.orderId = UUID.randomUUID().toString();
        this.action = action;
        this.optionName = optionName;
        this.amount = amount;
        this.limitPrice = limitPrice;
        this.username = username;
    }

    // constructor that supports exercise 1 and use tester as the username
    public TradeRequest(ActionType action, String optionName, int amount, double limitPrice) {
        this.orderId = UUID.randomUUID().toString();
        this.action = action;
        this.optionName = optionName;
        this.amount = amount;
        this.limitPrice = limitPrice;
        // נותנים ערך דיפולטיבי כי בתרגיל 1 יש רק משתמש אחד (הבודק)
        this.username = "Tester";
    }

    // --- Getters ---
    public String getOrderId() { return orderId; }
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