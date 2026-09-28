package internals;

import java.io.Serializable;
import java.util.*;

/**
 *  a base implementation of the Event interface.
 */
public class MarketEvent implements Event, Serializable {
    protected int id;
    protected String name;
    protected String description;
    protected int commissionPercent;
    protected CommissionType commissionType;
    protected List<String> options;
    protected String winningOption;
    protected EventStatus status;
    protected TradingMethods tradingMethod;

    // username -> (optionName -> amount of shares)
    protected Map<String, Map<String, Integer>> participantsHoldings = new HashMap<>();
    protected String marketMaker;

    public MarketEvent(String name, String description, int commissionPercent,
                         CommissionType commissionType, List<String> options, TradingMethods tradingMethod) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(commissionType, "CommissionType must not be null");
        Objects.requireNonNull(tradingMethod, "trading method must not be null");
        if(options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be null or empty");
        }
        if(commissionPercent < 0 || commissionPercent > 90){
            throw new IllegalArgumentException("commissionPercent must be between 0 and 90, right now it's " + commissionPercent);
        }

        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.options = options;
        this.tradingMethod = tradingMethod;
        this.status = EventStatus.NOT_STARTED;
        this.winningOption = null;
    }

    @Override
    public String getName() { return this.name; }

    @Override
    public String getDescription() { return this.description; }

    @Override
    public int getCommissionPercent() { return this.commissionPercent; }

    @Override
    public CommissionType getCommissionType() { return this.commissionType; }

    @Override
    public List<String> getOptions() { return Collections.unmodifiableList(this.options); }

    @Override
    public EventStatus getStatus() { return this.status; }

    @Override
    public String getWinningOption() { return this.winningOption; }

    @Override
    public double getPoolValue() { return this.tradingMethod.getPoolValue(); }

    @Override
    public int getOptionShares(String optionName) { return this.tradingMethod.getOptionShares(optionName); }

    @Override
    public TradingMethods getTradingMethod() {
        return this.tradingMethod;
    }

    @Override
    public void setMarketMaker(String username) {
        this.marketMaker = username;
    }

    @Override
    public String getMarketMaker() {
        return this.marketMaker;
    }

    /**
     *  validates the event state before passing the trade to the responsible tradingMethod
     */
    @Override
    public List<TradeResult> processTrade(TradeRequest request) {
        if (this.status != EventStatus.OPEN) {
            return List.of(new TradeResult(request.getOrderId(), request.getUsername(),false, request.getOptionName(), request.getAction(),0, 0, 0, "Event is not active"));
        }
        if (!this.options.contains(request.getOptionName()))
        {
            return List.of(new TradeResult(request.getOrderId(), request.getUsername(),false, request.getOptionName(), request.getAction(),0, 0, 0, "Unknown option: "+request.getOptionName()));
        }
        return this.tradingMethod.executeOrder(request);
    }

    @Override
    public double getOptionPrice(String optionName)
    {
        validateOption(optionName);
        return this.tradingMethod.getPrice(optionName);
    }

    @Override
    public List<TradeResult> getTradeHistory()
    {
        return this.tradingMethod.getTradeHistory();
    }

    @Override
    public TradingMethodType getEventTradingMethodType() {
        return this.tradingMethod.getTradingMethodType();
    }

    @Override
    public Map<String, Map<String, Integer>> getParticipantsHoldings()
    {
        return this.participantsHoldings;
    }

    @Override
    public void recordParticipant(String username, String optionName, int amount) {
        this.participantsHoldings.putIfAbsent(username, new HashMap<>());
        Map<String, Integer> userShares = this.participantsHoldings.get(username);
        userShares.put(optionName, userShares.getOrDefault(optionName, 0) + amount);
    }

    protected void validateOption(String optionName)
    {
        if(!this.options.contains(optionName))
        {
            throw new IllegalArgumentException("Unknown option:"+optionName);
        }
    }

    @Override
    public void openEvent() {
        if(this.status != EventStatus.NOT_STARTED) {
            throw new IllegalStateException("Event is already open or closed");
        }
        this.status = EventStatus.OPEN;
        this.tradingMethod.openMarket();
    }

    @Override
    public CloseMarketResult resolveEvent(String winningOption){
        if (this.status != EventStatus.OPEN) {
            throw new IllegalStateException("Only open events can be resolved");
        }
        validateOption(winningOption);
        this.winningOption = winningOption;
        this.status = EventStatus.CLOSED;
        return this.tradingMethod.closeMarket(winningOption);
    }
}
