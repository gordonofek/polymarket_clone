package internals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class User {
    private String name;
    private double balance;
    // in case a user goes negative balance we should allow that, but also block the user
    private boolean isBlocked = false;

    // Event name -> (Option Name -> Amount of Shares Owned)
    private Map<String, Map<String, Integer>> portfolio;

    // Tracks the name's of the events this user is a market maker for
    private Set<String> managedEvents = new HashSet<>();

    // Event name -> Total commission paid
    private Map<String, Double> commissionsPaid = new HashMap<>();

    // Event name -> (Option Name -> Total Money Spent)
    private Map<String, Map<String, Double>> optionInvestments = new HashMap<>();

    // Event name -> Total Payout received (used for profit/loss)
    private Map<String, Double> payoutsReceived = new HashMap<>();

    // deposits and withdrawals, oldest first
    private List<BalanceChange> balanceHistory = new ArrayList<>();

    public User(String name) {
        this.name = name;
        this.balance = 0.0;
        this.portfolio = new HashMap<>();
    }

    public User(String name, double balance, Set<String> managedEvents) {
        this.name = name;
        this.balance = balance;
        this.managedEvents = managedEvents;
        this.portfolio = new HashMap<>();
    }

    public String getName() {return name;}
    public double getBalance() {return balance;}
    public Map<String, Map<String, Integer>> getPortfolio() {return this.portfolio;}

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

    public void addShares(String eventName, String optionName, int amount) {
        portfolio.putIfAbsent(eventName, new HashMap<>());
        Map<String, Integer> eventShares = portfolio.get(eventName);
        eventShares.put(optionName, eventShares.getOrDefault(optionName, 0) + amount);
    }

    public void addCommissionPaid(String eventName, double amount) {
        commissionsPaid.put(eventName, commissionsPaid.getOrDefault(eventName, 0.0) + amount);
    }

    public void addOptionInvestment(String eventName, String optionName, double amount) {
        optionInvestments.putIfAbsent(eventName, new HashMap<>());
        Map<String, Double> eventInvestments = optionInvestments.get(eventName);
        eventInvestments.put(optionName, eventInvestments.getOrDefault(optionName, 0.0) + amount);
    }

    public void addPayoutReceived(String eventName, double amount) {
        payoutsReceived.put(eventName, payoutsReceived.getOrDefault(eventName, 0.0) + amount);
    }

    public void addManagedEvent(String eventName) {
        this.managedEvents.add(eventName);
    }

    public boolean isMarketMakerFor(String eventName) {
        return this.managedEvents.contains(eventName);
    }

    public boolean isBlocked() {
        return this.isBlocked;
    }

    public Set<String> getManagedEvents() {
        return managedEvents;
    }

    public double getCommissionPaidForEvent(String eventName) {
        return commissionsPaid.getOrDefault(eventName, 0.0);
    }

    public double getInvestmentForOption(String eventName, String optionName) {
        return optionInvestments.getOrDefault(eventName, new HashMap<>()).getOrDefault(optionName, 0.0);
    }

    public double getPayoutForEvent(String eventName) {
        return payoutsReceived.getOrDefault(eventName, 0.0);
    }

    public Map<String, Map<String, Double>> getOptionInvestments() {
        return this.optionInvestments;
    }

    // records a deposit/withdrawal; call after the balance was changed
    public void addBalanceChange(boolean isDeposit, double amount) {
        balanceHistory.add(new BalanceChange(isDeposit, amount, this.balance, System.currentTimeMillis()));
    }

    public List<BalanceChange> getBalanceHistory() {
        return balanceHistory;
    }
}
