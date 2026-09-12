package internals;

import java.io.Serializable;
import java.util.*;

public class OrderBookTradingMethod implements TradingMethods, Serializable {

    private int baseValue; // the pay value for the wining option
    private int initialInvestment; // the amount Market Maker first invested to open the event
    private boolean allowMint; // option we can turn on to allow the minting mechanism

    private int commissionPercent;
    private CommissionType commissionType;

    // holds the requests(buy or sell) of each option in an ordered map from
    // which we can get cheapest buy option and the most expensive sell option first
    // you should look at the word doc of the task if you are confused
    private Map<String, NavigableMap<Double, Queue<TradeRequest>>> buy_requests;
    private Map<String, NavigableMap<Double, Queue<TradeRequest>>> sell_requests;

    private Map<String, Double> lastPrices; // Tracks the LAST price for UI
    private double poolValue;               // Tracks the event's internal money that MM manages
    private List<TradeResult> tradeHistory;

    // counter to count the number of minted sets of stocks
    // (set is one baseValue that includes all the option,
    // even if the sum is bigger then baseValue)
    private int totalMintedSets = 0;

    // Constructor that CXMLDataLoader uses
    public OrderBookTradingMethod(int baseValue, int initialInvestment, boolean allowMint,
                                  int commissionPercent, CommissionType commissionType,
                                  List<String> optionNames) {

        this.baseValue = baseValue;
        this.initialInvestment = initialInvestment;
        this.allowMint = allowMint;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.poolValue = 0.0;

        this.tradeHistory = new ArrayList<>();
        this.lastPrices = new HashMap<>();
        this.buy_requests = new HashMap<>();
        this.sell_requests = new HashMap<>();

        // init the queues
        for (String option : optionNames) {
            this.lastPrices.put(option, 0.0);

            // in polymarket and stock exchange the one how decide the price
            // is the order which set the longer time in the buy or sell queues
            // this why potential buyer get the lowest price if we scan the sell offers
            // from lowest to highest. A potential buyer on the other hand can get the
            // highest buy price if we scan the buy offers from highest to lowest

            // the buy queues are ordered from the highest bid to the lowest
            // because when we check if there matching buy order for a sell order
            // we went to get the price offer for the seller, the highest offer
            this.buy_requests.put(option, new TreeMap<>(Collections.reverseOrder()));

            // the sell queues are ordered from the lowest ask to the highest
            // because when we check if there matching sell order for a buy order
            // we went to get the best price for the buyer, the cheapest
            this.sell_requests.put(option, new TreeMap<>());
        }
    }

    public Map<Double, Queue<TradeRequest>> get_option_buy_requests(String optionName)
    {
        return buy_requests.get(optionName);
    }

    public Map<Double, Queue<TradeRequest>> get_option_sell_requests(String optionName) {
        return sell_requests.get(optionName);
    }

    @Override
    public TradingMethodType getTradingMethodType()
    {
        return TradingMethodType.ORDER_BOOK;
    }

    public double getLastPrice(String optionName)
    {
        return lastPrices.get(optionName);
    }

    public double getHighestBid(String optionName)
    {
        if (buy_requests.get(optionName).isEmpty()) {
            return 0.0;
        }
        return buy_requests.get(optionName).firstKey();
    }

    public double getLowestAsk(String optionName)
    {
        if (sell_requests.get(optionName).isEmpty()) {
            return 0.0;
        }
        return sell_requests.get(optionName).firstKey();
    }

    public double getMidPrice(String optionName)
    {
        return (getHighestBid(optionName) + getLowestAsk(optionName))/2;
    }

    public double getSpread(String optionName)
    {
        return getHighestBid(optionName) - getLowestAsk(optionName);
    }

    // helper function that execute trade order of optionName, it can be buy or sell.
    private List<TradeResult> matchOrders(String username,String optionName ,ActionType incomingAction)
    {
        List<TradeResult> currentMatches = new ArrayList<>();

        while(!buy_requests.get(optionName).isEmpty() && !sell_requests.get(optionName).isEmpty())
        {
            Map.Entry<Double, Queue<TradeRequest>> bestBuyEntry =
                    buy_requests.get(optionName).firstEntry();

            Map.Entry<Double, Queue<TradeRequest>> bestSellEntry =
                    sell_requests.get(optionName).firstEntry();

            double buy_price = bestBuyEntry.getKey();
            double sell_price = bestSellEntry.getKey();

            // if the buy price is lower then the sell price we can't preform trade
            if (buy_price < sell_price) {
                break;
            }

            // extract the Queue<TradeRequest> for the buy_price and sell_price
            Queue<TradeRequest> buy_queue = bestBuyEntry.getValue();
            Queue<TradeRequest> sell_queue = bestSellEntry.getValue();

            // get the most costly buy request and the cheapest sell request
            TradeRequest buy_request = buy_queue.peek();
            TradeRequest sell_request = sell_queue.peek();

            // finding the amount of shares available to be traded in this price point
            int tradedShares = Math.min(buy_request.getAmount(), sell_request.getAmount());

            buy_request.setAmount(buy_request.getAmount() - tradedShares);
            sell_request.setAmount(sell_request.getAmount() - tradedShares);

            if(buy_request.getAmount() == 0)
            {
                buy_queue.poll(); // מעיפים את הבקשה מהתור
                if (buy_queue.isEmpty()) { // רק אם התור התרוקן לגמרי!
                    buy_requests.get(optionName).pollFirstEntry(); // מוחקים את המחיר מהספר
                }
            }

            if(sell_request.getAmount() == 0)
            {
                sell_queue.poll(); // מעיפים את הבקשה מהתור
                if (sell_queue.isEmpty()) { // רק אם התור התרוקן לגמרי!
                    sell_requests.get(optionName).pollFirstEntry(); // מוחקים את המחיר מהספר
                }
            }

            // check who has the older price offer, buy or sell.
            double executionPrice;
            if (incomingAction == ActionType.BUY) {
                executionPrice = sell_price;
            } else {
                executionPrice = buy_price;
            }

            // updating the lastPrice for this specific option we just traded
            this.lastPrices.put(optionName, executionPrice);

            double totalTransactionCost = tradedShares * executionPrice;

            //calc commission(if there is ON_PURCHASE in this event) and creating TradeResult to add to tradeHistory
            double commission = calculateCommission(totalTransactionCost, CommissionType.ON_PURCHASE);

            TradeResult trade1 = new TradeResult(buy_request.getOrderId(), username,true, optionName,
                    ActionType.BUY, tradedShares, totalTransactionCost,
                    commission, "Bought successfully");

            TradeResult trade2 = new TradeResult(sell_request.getOrderId(), username,true, optionName,
                    ActionType.SELL, tradedShares, totalTransactionCost,
                    0.0, "Sold successfully");

            currentMatches.add(trade1);
            currentMatches.add(trade2);

            this.tradeHistory.add(trade1);
            this.tradeHistory.add(trade2);
        }
        return currentMatches;
    }

    // Checks if allowMint is true and looks across all options
    // (YES and NO or multichoice) to see if combined bids >= baseValue.
    // If so, it creates new shares and updates poolValue
    private List<TradeResult> attemptMint(String username,String incomingOptionName)
    {
        List<TradeResult> currentMatches = new ArrayList<>();

        // Check if the event even allows minting
        if (!this.allowMint) {
            return null;
        }

        // The "Infinite" Loop - keeps running as long as minting is possible
        while (true)
        {
            double sum = 0.0;

            // we use a very large number(2,147,483,647)
            // as a comparison to amount of shares of one option
            // a user wents to buy. we went to the min of those two numbers
            // that why we use such a big number to always choose
            int minShares = Integer.MAX_VALUE;

            // Verify every option has a buyer AND calculate the sum,
            // we need to have every option to loss money as Market Maker
            // if the option we didn't have is the winning option and we only
            // got money for lossing options we minted
            for (NavigableMap<Double, Queue<TradeRequest>> map : buy_requests.values()) {
                // If even ONE option is empty, we cannot buy every option.
                // We must stop the entire minting process.
                if (map.isEmpty()) {
                    return null;
                }
                // Add the highest bid of this option to our total sum
                sum += map.firstKey();
            }

            // Verify the sum surpasses or equals the base value, if not we don't mint
            if (sum < this.baseValue) {
                // The buyers aren't offering enough combined money.
                // Break the while loop and finish the function.
                break;
            }

            // Find the bottleneck, minimum sets of all the shares among all top paying buyers
            for (NavigableMap<Double, Queue<TradeRequest>> map : buy_requests.values()) {
                TradeRequest topBuyer = map.firstEntry().getValue().peek();
                minShares = Math.min(minShares, topBuyer.getAmount());
            }

            // STEP 4: Execute the Mint (Deduct shares and clean up)
            for (Map.Entry<String, NavigableMap<Double, Queue<TradeRequest>>> entry : buy_requests.entrySet()) {
                String optionName = entry.getKey();
                NavigableMap<Double, Queue<TradeRequest>> map = entry.getValue();

                Queue<TradeRequest> topQueue = map.firstEntry().getValue();
                TradeRequest topBuyer = topQueue.peek();

                // Deduct the bottleneck amount from this buyer
                topBuyer.setAmount(topBuyer.getAmount() - minShares);

                double executionPrice;

                if (optionName.equals(incomingOptionName))
                {
                    // using the sum of all the bids we calc before we can use
                    // basic math to calc how much incomingOptionName(new order) would pay
                    double takerBid = map.firstKey();
                    double makerSum = sum - takerBid;
                    executionPrice = this.baseValue - makerSum;
                }
                else
                {
                    // This is the older order
                    // They pay exactly what they bid.
                    executionPrice = map.firstKey();
                }

                // updating the lastPrice for this specific option we just minted
                this.lastPrices.put(optionName, executionPrice);

                // 3. Calculate total cost and commission for this buyer
                double totalTransactionCost = minShares * executionPrice;
                double commission = calculateCommission(totalTransactionCost, CommissionType.ON_PURCHASE);

                // 4. Create the receipt and add it to the history
                TradeResult trade = new TradeResult(topBuyer.getOrderId(), username,true,
                        optionName, ActionType.BUY, minShares, totalTransactionCost, commission,
                        "Mint successful");

                currentMatches.add(trade);
                this.tradeHistory.add(trade);

                // Clean up: If this buyer got all their shares, remove them safely
                if (topBuyer.getAmount() == 0) {
                    topQueue.poll();
                    if (topQueue.isEmpty()) { // If the queue for this price is now empty
                        map.pollFirstEntry(); // Remove the price point from the book
                    }
                }

                // (Optional) You can also generate a TradeResult here to log the mint in tradeHistory
            }

            // STEP 5: Update the Event's Pool Value
            // The money goes to the event account, and the shares were given to the buyers
            this.poolValue += (minShares * this.baseValue);

            // add to the counter of mintedSets
            this.totalMintedSets += minShares;
        }
        return currentMatches;
    }

    @Override
    public double getPrice(String optionName) {
        return this.lastPrices.getOrDefault(optionName, 0.0);
    }

    @Override
    public double getPoolValue() {
        return poolValue;
    }

    public int getInitialInvestment() {return this.initialInvestment;}

    public int getBaseValue() {return this.baseValue;}

    @Override
    public List<TradeResult> executeOrder(TradeRequest request)
    {
        // get the attributes of the request
        String id = request.getOrderId();
        ActionType type = request.getAction();
        String name = request.getOptionName();
        String username = request.getUsername();
        double price = request.getLimitPrice();

        List<TradeResult> allExecutedTrades = new ArrayList<>();

        // Validation Check: Does this option actually exist in our market?
        if (!buy_requests.containsKey(name)) {
            // מחזירים רשימה שמכילה את התוצאה הבודדת של השגיאה
            TradeResult errorResult = new TradeResult(request.getOrderId(),
                    username, false, name, type, 0, 0.0,
                    0.0, "Error: Option does not exist.");
            allExecutedTrades.add(errorResult);
            return allExecutedTrades;
        }

        // Route the order safely (because we know 'name' exists!)
        // Adds the order to the sell or buy requests
        if(type == ActionType.SELL) {
            sell_requests.get(name).computeIfAbsent(price, k -> new LinkedList<>()).add(request);
        } else {
            buy_requests.get(name).computeIfAbsent(price, k -> new LinkedList<>()).add(request);
        }

        allExecutedTrades.addAll(matchOrders(username, name, type));
        if (this.allowMint) {
            // בגלל ש-attemptMint יכולה להחזיר null, צריך לבדוק לפני שמוסיפים
            List<TradeResult> mintResults = attemptMint(username, name);
            if (mintResults != null) {
                allExecutedTrades.addAll(mintResults);
            }
        }

        // אם לא הייתה שום עסקה (הרשימה ריקה), נייצר הודעת המתנה
        if (allExecutedTrades.isEmpty()) {
            allExecutedTrades.add(new TradeResult(request.getOrderId(),
                    username, true, name, type, 0,
                    0.0, 0.0,
                    "Order accepted and waiting in book."));
        }

        return allExecutedTrades;
    }

    @Override
    public double calculateCommission(double baseAmount, CommissionType currentPhase) {
        // check that we have matching commissionType if so calc
        if (this.commissionType == currentPhase) {
            return baseAmount * (this.commissionPercent / 100.0);
        }
        // else return 0.0
        return 0.0;
    }

    @Override
    public int getOptionShares(String optionName) {
        // Calculate how many sets the Market Maker bought when opening the market
        int initialSets = this.initialInvestment / this.baseValue;

        // Total shares = Initial MM sets + Sets minted later by users
        return initialSets + this.totalMintedSets;
    }

    @Override
    public double openMarket() {
        // init the poolValue with the investment amount we got from constructor, start the event
        this.poolValue += this.initialInvestment;

        // return this value so we now how much we invested into the event
        return this.initialInvestment;
    }

    @Override
    public CloseMarketResult closeMarket(String winningOption) {
        // Get the TRUE total of winning shares
        int totalWinningShares = getOptionShares(winningOption);

        // Calculate total payout (Every winning share gets the exact baseValue)
        double totalPayout = totalWinningShares * this.baseValue;

        // Calculate the final commission
        double commission = calculateCommission(totalPayout, CommissionType.ON_CLOSE);

        // Net payout to users (and the MM for any unsold initial shares)
        double netPayout = totalPayout - commission;

        // Calculate what is left in the internal pool
        double leftoverSubsidy = this.poolValue - totalPayout;

        return new CloseMarketResult(winningOption, totalPayout, commission, netPayout, leftoverSubsidy);
    }

    @Override
    public List<TradeResult> getTradeHistory() {
        return this.tradeHistory;
    }
}
