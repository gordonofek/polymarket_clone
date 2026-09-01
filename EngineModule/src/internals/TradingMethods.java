package internals;

import java.util.List;

public interface TradingMethods {
    // calc and gets price of that type of stock(YES or NO or Multichoice...)
    double getPrice(String optionName);

    // calc and gets the amount of money invested into the event
    double getPoolValue();

    // execute a trade order of the user on the event
    TradeResult executeOrder(TradeRequest request);

    // calc the commission participants owe the Market Maker
    double calculateCommission(double baseAmount, CommissionType currentPhase);

    // gets the total amount of shares bought for a specific option
    int getOptionShares(String optionName);

    // activates the event and deducts the initial subsidy from the event's internal MM account
    double openMarket();

    // distributes funds and takes final commissions
    CloseMarketResult closeMarket(String winningOption);

    List<TradeResult> getTradeHistory();
}
