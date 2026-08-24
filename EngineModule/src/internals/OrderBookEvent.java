package internals;

import java.util.List;

public class OrderBookEvent extends AbstractEvent{

    public OrderBookEvent(int id, String name, String description, int commissionPercent, CommissionType commissionType, List<String> options, TradingMethods tradingMethod)
    {
        super(id, name, description, commissionPercent, commissionType, options, tradingMethod);
    }

    /**
     *  Don't know yet if LMSREvent openEvent and resolveEvent will look identical to OrderBookEvent, so for now left them here. maybe
     */
    @Override
    public void openEvent()
    {
        //TODO
    }

    @Override
    public CloseMarketResult resolveEvent(String winningOption)
    {
        //TODO
        return this.tradingMethod.closeMarket(winningOption);
    }

}
