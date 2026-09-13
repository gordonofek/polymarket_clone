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

    // Event id -> Total commission paid
    private Map<Integer, Double> commissionsPaid = new HashMap<>();

    // Event id -> (Option Name -> Total Money Spent)
    private Map<Integer, Map<String, Double>> optionInvestments = new HashMap<>();

    // Event id -> Total Payout received (used for profit/loss)
    private Map<Integer, Double> payoutsReceived = new HashMap<>();

    public User(String name) {
        this.name = name;
        this.balance = 0.0;
        this.portfolio = new HashMap<>();
    }

    public User(String name, double balance, Set<Integer> managedEvents) {
        this.name = name;
        this.balance = balance;
        this.managedEvents = managedEvents;
        this.portfolio = new HashMap<>();
    }

    public String getName() {return name;}
    public double getBalance() {return balance;}
    public Map<Integer, Map<String, Integer>> getPortfolio() {return this.portfolio;}

    public void addFunds(double amount) {
        this.balance += amount;

        // decided that if the user goes back into positive balance, then he gets unblocked.
        if (this.balance >= 0 && this.isBlocked) {
            this.isBlocked = false;
        }
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

    public void addCommissionPaid(int eventId, double amount) {
        commissionsPaid.put(eventId, commissionsPaid.getOrDefault(eventId, 0.0) + amount);
    }

    public void addOptionInvestment(int eventId, String optionName, double amount) {
        optionInvestments.putIfAbsent(eventId, new HashMap<>());
        Map<String, Double> eventInvestments = optionInvestments.get(eventId);
        eventInvestments.put(optionName, eventInvestments.getOrDefault(optionName, 0.0) + amount);
    }

    public void addPayoutReceived(int eventId, double amount) {
        payoutsReceived.put(eventId, payoutsReceived.getOrDefault(eventId, 0.0) + amount);
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

    public Set<Integer> getManagedEvents() {
        return managedEvents;
    }

    public double getCommissionPaidForEvent(int eventId) {
        return commissionsPaid.getOrDefault(eventId, 0.0);
    }

    public double getInvestmentForOption(int eventId, String optionName) {
        return optionInvestments.getOrDefault(eventId, new HashMap<>()).getOrDefault(optionName, 0.0);
    }

    public double getPayoutForEvent(int eventId) {
        return payoutsReceived.getOrDefault(eventId, 0.0);
    }

    public Map<Integer, Map<String, Double>> getOptionInvestments() {
        return this.optionInvestments;
    }
}
