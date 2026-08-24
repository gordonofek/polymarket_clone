package internals;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *  a base implementation of the Event interface.
 */
public abstract class AbstractEvent implements Event, Serializable {
    protected int id;
    protected String name;
    protected String description;
    protected int commissionPercent;
    protected CommissionType commissionType;
    protected List<String> options;
    protected String winningOption;
    protected EventStatus status;
    protected TradingMethods tradingMethod;

    public AbstractEvent(int id, String name, String description, int commissionPercent,
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


        this.id = id;
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
    public int getId() { return this.id; }

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

    /**
     *  validates the event state before passing the trade to the responsible tradingMethod
     */
    @Override
    public TradeResult processTrade(TradeRequest request) {
        if (this.status != EventStatus.OPEN) {
            return new TradeResult(false, request.optionName(), 0, 0, 0, "Event is not active");
        }
        if (!this.options.contains(request.optionName()))
        {
            return new TradeResult(false, request.optionName(), 0, 0, 0, "Unknown option: "+request.optionName());
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

    protected void validateOption(String optionName)
    {
        if(!this.options.contains(optionName))
        {
            throw new IllegalArgumentException("Unknown option:"+optionName);
        }
    }
}
