package internals;

import java.util.List;

/**
 *  A Logarithmic Market Scoring Rule (LMSR) Event type
 *  This event uses an automated market maker pool to calculate prices dynamically
 */
public class LMSREvent extends AbstractEvent{

    public LMSREvent(int id, String name, String description, int commissionPercent, CommissionType commissionType, List<String> options, TradingMethods tradingMethod)
    {
        // pass shared data to the AbstractEvent parent class
        super(id, name, description, commissionPercent, commissionType, options, tradingMethod);
    }

    @Override
    public void openEvent() {
        if(this.status != EventStatus.NOT_STARTED)
        {
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


