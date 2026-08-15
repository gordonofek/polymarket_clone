package internals;

import java.util.List;

public interface Event {

    String getName();
    String getDescription();
    int getId();

    // returns the commission percentage (0 to 90)
    int getCommissionPercent();

    // returns the type of the commission (on-purchase or on-close)
    CommissionType getCommissionType();

    // returns the list of the possible options ([Yes, No], [Argentina, Spain, England])
    List<String> getOptions();

    // return the status of the event (Not-Started, Open, Closed)
    EventStatus getStatus();

    // gets the current price for specific option
    double getOptionPrice(String optionName);

    /**
     * returns the current value of the event's pool.
     * For LMSR represents the total cost function state.
     * For OrderBook represents the balance of the internal event account
     */
    double getPoolValue();

    // gets the history of all trades in the event
    List<TradeResult> getTradeHistory();

    /** opens the event, transitions the event from Not-Started to Open
        for LMSR: The Market Maker pays the initial subsidy
        for Order Book: The Market Maker buys the initial shares
    */
    void openEvent();

    // Delegates a buy or sell request to the right trading method and returns the trade result
    TradeResult processTrade(TradeRequest request);

    // transitions the event from Open to Closed. Calculates the payouts.
    CloseMarketResult resolveEvent(String winningOption);

    String getWinningOption();
}
