package internals;

import java.util.List;

public class LMSREvent implements Event {

    private int id;
    private String name;
    private String description;
    private int commissionPercent;
    private CommissionType commissionType;
    private List<String> options;

    private EventStatus status;
    private TradingMethods tradingMethod;

    public LMSREvent(int id, String name, String description, int commissionPercent, CommissionType commissionType, List<String> options, TradingMethods tradingMethod)
    {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.options = options;
        this.status = EventStatus.NOT_STARTED;
        this.tradingMethod = tradingMethod;
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

    @Override
    public CloseMarketResult resolveEvent(String winningOption){
        if (this.status != EventStatus.OPEN) {
            throw new IllegalStateException("Only open events can be resolved");
        }
        this.status = EventStatus.CLOSED;
        return this.tradingMethod.closeMarket(winningOption);
    }

}
