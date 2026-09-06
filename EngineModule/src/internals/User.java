package internals;

import java.util.HashMap;
import java.util.Map;

public class User {
    private int uid;
    private String name;
    private double balance;

    // Event ID -> (Option Name -> Amount of Shares Owned)
    private Map<Integer, Map<String, Integer>> portfolio;

    public User(String name) {
        this.name = name;
        this.balance = 0.0;
        this.portfolio = new HashMap<>();
    }

    public String getName() {return name;}
    public double getBalance() {return balance;}

    public void addFunds(double amount) {
        this.balance += amount;
    }

    public boolean deductFunds(double amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    public void addShares(int eventId, String optionName, int amount) {
        portfolio.putIfAbsent(eventId, new HashMap<>());
        Map<String, Integer> eventShares = portfolio.get(eventId);
        eventShares.put(optionName, eventShares.getOrDefault(optionName, 0) + amount);
    }
}
