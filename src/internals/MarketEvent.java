package internals;

import java.util.List;


/**
 *  A general implementation of an Event in the guess market.
 */
public class MarketEvent implements Event{

    private int id;
    private String name;
    private String description;
    private int commissionPercent;
    private CommissionType commissionType;
    private List<String> options;
    private TradingMethods tradingMethod;
    private EventStatus status;
    private String winningOption;

    public MarketEvent(int id, String name, String description, int commissionPercent, CommissionType commissionType, List<String> options, TradingMethods tradingMethod)
    {
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
    public int getId(){
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public int getCommissionPercent() {
        return this.commissionPercent;
    }

    @Override
    public CommissionType getCommissionType() {
        return this.commissionType;
    }

    @Override
    public List<String> getOptions() {
        return this.options;
    }

    @Override
    public EventStatus getStatus() {
        return this.status;
    }

    @Override
    public double getOptionPrice(String optionName)
    {
        return this.tradingMethod.getPrice(optionName);
    }

    @Override
    public double getPoolValue()
    {
        return this.tradingMethod.getPoolValue();
    }

    @Override
    public List<TradeResult> getTradeHistory()
    {
        return this.tradingMethod.getTradeHistory();
    }

    @Override
    public void openEvent() {
        if(this.status != EventStatus.NOT_STARTED)
        {
            throw new IllegalStateException("Event is already open or closed");
        }
        this.status = EventStatus.OPEN;
    }

    @Override
    public TradeResult processTrade(TradeRequest request) {
        if (this.status != EventStatus.OPEN){
            return new TradeResult(false, request.optionName(),0, 0, 0, "Trade Failed: Event is not active");
        }
        return this.tradingMethod.executeOrder(request);
    }

    public String getWinningOption() {
        return winningOption;
    }

    @Override
    public CloseMarketResult resolveEvent(String winningOption){
        if (this.status != EventStatus.OPEN) {
            throw new IllegalStateException("Only open events can be resolved");
        }
        this.status = EventStatus.CLOSED;
        this.winningOption = winningOption;
        return this.tradingMethod.closeMarket(winningOption);
    }
}


