package internals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class User {
    private String name;
    private double balance;
    // in case a user goes negative balance we should allow that, but also block the user
    private boolean isBlocked = false;

    // Event ID -> (Option Name -> Amount of Shares Owned)
    private Map<Integer, Map<String, Integer>> portfolio;

    // Tracks the id's of the events this user is a market maker for
    private Set<Integer> managedEvents = new HashSet<>();

    public User(String name) {
        this.name = name;
        this.balance = 0.0;
        this.portfolio = new HashMap<>();
    }

    public String getName() {return name;}
    public double getBalance() {return balance;}
    public Map<Integer, Map<String, Integer>> getPortfolio() {return this.portfolio;}

    public void addFunds(double amount) {
        this.balance += amount;
    }

    public void deductFunds(double amount) {
        this.balance -=amount;
        if(this.balance < 0) {
            this.isBlocked = true;
        }
    }

    public void addShares(int eventId, String optionName, int amount) {
        portfolio.putIfAbsent(eventId, new HashMap<>());
        Map<String, Integer> eventShares = portfolio.get(eventId);
        eventShares.put(optionName, eventShares.getOrDefault(optionName, 0) + amount);
    }

    public void addManagedEvent(int eventId) {
        this.managedEvents.add(eventId);
    }

    public boolean isMarketMakerFor(int eventId) {
        return this.managedEvents.contains(eventId);
    }

    public boolean isBlocked() {
        return this.isBlocked;
    }
}
