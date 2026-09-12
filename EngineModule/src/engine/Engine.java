package engine;
import internals.*;

import engine.dto.*;
import engine.files.FileHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Engine implements EngineInterface {

    // כאן המנוע שומר את כל האירועים הפעילים והסגורים (למשל במפה לפי ID)
    private EventManager eventManager;
    private UserManager userManager;

    public Engine() {
        this.eventManager = new EventManager();
        this.userManager = new UserManager();
    }

    @Override
    public void loadDataFromXML(String filePath) throws Exception {
        // creates a new XML_loader obj, and then load XML from filePath
        XMLDataLoader loader = new XMLDataLoader();
        ParsedData data = loader.loadData(filePath);

        eventManager.clearEvents();
        eventManager.addEventList(data.events());

        userManager.clearUsers();
        userManager.addUserList(data.users());
    }

    @Override
    public boolean isDataLoaded() {
        return !eventManager.isRegistryEmpty();
    }

    @Override
    public List<EventSummaryDTO> getAllEvents() {
        // using stream to turn all the Events into EventSummaryDTO,
        // and then putting it in a list and returning it
        return eventManager.getAllEvents().stream()
                .map(event -> new EventSummaryDTO(
                        event.getId(),
                        event.getName(),
                        event.getDescription(),
                        event.getStatus().name(), // Converts the EventStatus enum to a String
                        event.getCommissionPercent(),
                        event.getCommissionType().name(),
                        event.getOptions(),
                        event.getEventTradingMethodType().name()
                ))
                .toList();
    }

    @Override
    public List<EventSummaryDTO> getActiveEvents() {
        // using stream to turn all active Events into EventSummaryDTO,
        // and then putting it in a list and returning it
        List<EventSummaryDTO> list = new ArrayList<>();

        for (Event event : eventManager.getAllEvents()) {
            // Only process the event if its status is OPEN
            if (event.getStatus() == EventStatus.OPEN) {
                EventSummaryDTO dto = new EventSummaryDTO(
                        event.getId(),
                        event.getName(),
                        event.getDescription(),
                        event.getStatus().name(), // Converts the EventStatus enum to a String
                        event.getCommissionPercent(),
                        event.getCommissionType().name(),
                        event.getOptions(),
                        event.getEventTradingMethodType().name()
                );
                list.add(dto);
            }
        }

        return list;
    }

    @Override
    public EventDetailsDTO getEventState(int eventId) {
        // 1. Fetch the raw domain object from your data structure
        Event event = eventManager.getEvent(eventId);

        // 2. Cleverly use a Stream to calculate the total commission from the history
        double totalCommission = event.getTradeHistory().stream()
                .mapToDouble(trade -> trade.commissionPaid()) // assuming TradeResult has a commission() method
                .sum();

        // 3. Cleverly use a Stream to build the List<OptionDetailsDTO>
        List<OptionDetailsDTO> optionsDTOs = event.getOptions().stream()
                .map(optionName -> {
                    // נתונים בסיסיים שקיימים בכל אירוע
                    double currentPrice = event.getOptionPrice(optionName);
                    int shares = event.getOptionShares(optionName);

                    // משתני עזר לסטטיסטיקות שיקבלו ערך רק אם זה Order Book
                    double bid = 0.0, ask = 0.0, mid = 0.0, spread = 0.0;

                    // שולפים את שיטת המסחר האמיתית
                    TradingMethods method = event.getTradingMethod();

                    // שימוש ב-Pattern Matching (זמין ב-Java מתקדם)
                    // כדי לבדוק אם זה Order Book ולהמיר אותו באותה שורה
                    if (method instanceof OrderBookTradingMethod obMethod) {
                        bid = obMethod.getHighestBid(optionName);
                        ask = obMethod.getLowestAsk(optionName);
                        mid = obMethod.getMidPrice(optionName);
                        spread = obMethod.getSpread(optionName);
                    }

                    // מחזירים את ה-DTO השלם!
                    return new OptionDetailsDTO(
                            optionName,
                            currentPrice,
                            shares,
                            bid,
                            ask,
                            mid,
                            spread
                    );
                })
                .toList();

        // 4. Cleverly use a Stream to map the raw trade history to TradeRecordDTOs
        List<TradeRecordDTO> historyDTOs = event.getTradeHistory().stream()
                .map(trade -> new TradeRecordDTO(
                        ActionType.BUY, // Defaulting to BUY based on current LMSR logic
                        trade.optionName(),
                        trade.sharesProcessed(),
                        trade.totalCost() // assuming TradeResult has a cost/price method
                ))
                .toList();

        // 5. Figure out the winning option (null if not closed)
        String winner = (event.getStatus() == EventStatus.CLOSED) ? event.getWinningOption() : null;

        // 6. Finally, return the new object using the constructor
        return new EventDetailsDTO(
                event.getId(),
                event.getName(),
                event.getStatus().name(),
                event.getPoolValue(),
                totalCommission,
                optionsDTOs,
                historyDTOs,
                winner
        );
    }

    @Override
    public List<ParticipantHoldingDTO> getEventParticipants(int eventId)
    {
        Event event = eventManager.getEvent(eventId);
        Map<String, Map<String, Integer>> ledger = event.getParticipantsHoldings();

        List<ParticipantHoldingDTO> holdings = ledger.entrySet().stream()
                .map(entry -> {
                    String participantName = entry.getKey();
                    Map<String, Integer> shares = new HashMap<>(entry.getValue());
                    return new ParticipantHoldingDTO(participantName, shares);
                }
        ).toList();

        return holdings;
    }

    @Override
    public TradeReceiptDTO placeOrder(String username, int eventId, String optionName, int amount, ActionType action, double limitPrice) {
        Event event = eventManager.getEvent(eventId);
        User initiatingUser = userManager.getUser(username);

        // check if the user went into negative balance, if yes then he is blocked and thus can't trade.
        if (initiatingUser.isBlocked()) {
            return new TradeReceiptDTO(false, "User is blocked due to negative balance.", 0, 0);
        }

        // 1. Capture the LIST of results instead of a single result
        List<TradeResult> results = event.processTrade(new TradeRequest(action, optionName, amount, limitPrice, username));

        double totalCostForInitiator = 0.0;
        double totalCommissionForInitiator = 0.0;
        boolean overallSuccess = false;
        String finalMessage = "Order processed";

        // 2. Iterate through every receipt generated by this action
        for (TradeResult res : results) {

            if (!res.isSuccess()) {
                // If it failed (e.g., option doesn't exist), capture the error message
                finalMessage = res.message();
                continue;
            }

            overallSuccess = true; // At least one part of the trade worked

            // 3. Fetch the specific user for THIS receipt (could be the buyer or the seller!)
            User involvedUser = userManager.getUser(res.username());

            // 4. Apply the financial changes based on the action in the receipt
            if (res.action() == ActionType.BUY) {
                involvedUser.deductFunds(res.totalCost());
                involvedUser.addShares(eventId, res.optionName(), res.sharesProcessed());
                event.recordParticipant(res.username(), res.optionName(), res.sharesProcessed());
            } else if (res.action() == ActionType.SELL) {
                involvedUser.addFunds(res.totalCost());
                involvedUser.addShares(eventId, res.optionName(), -res.sharesProcessed());
                event.recordParticipant(res.username(), res.optionName(), -res.sharesProcessed());
            }

            // 5. Accumulate the costs/commissions ONLY for the user who initiated the request (for the UI popup)
            if (res.username().equals(username)) {
                totalCostForInitiator += res.totalCost();
                totalCommissionForInitiator += res.commissionPaid();
                finalMessage = res.message(); // Use the success message meant for them
            }
        }

        // 6. Return a summary DTO specifically for the user who clicked the button
        return new TradeReceiptDTO(overallSuccess, finalMessage, totalCostForInitiator, totalCommissionForInitiator);
    }

    @Override
    public TradeReceiptDTO startEvent(String username, int eventId) {
        Event event = eventManager.getEvent(eventId);
        User user = userManager.getUser(username);

        if (event.getStatus() != EventStatus.NOT_STARTED) {
            return new TradeReceiptDTO(false, "Event is already active.",0,0);
        }
        double cost = 0;
        int sharesToGive = 0;

        TradingMethods method = event.getTradingMethod();
        if(method instanceof OrderBookTradingMethod ob) {
            cost = ob.getInitialInvestment();
            sharesToGive = (int) (cost / ob.getBaseValue());
        }
        // LMSR
        else {
            cost = method.getPoolValue();
        }

        if (user.getBalance() < cost) {
            return new TradeReceiptDTO(false, String.format("Insufficient funds! You need $%.2f to start the event.", cost),0 ,0);
        }
        // deducting funds and opening the event
        user.deductFunds(cost);
        event.openEvent();

        // granting the market maker the initial order book shares for EVERY option
        if (method instanceof OrderBookTradingMethod && sharesToGive > 0) {
            for (String optionName : event.getOptions()) {
                user.addShares(eventId, optionName, sharesToGive);
                event.recordParticipant(username, optionName, sharesToGive);
            }
        }
        return new TradeReceiptDTO(true, "Event started successfully!", cost, 0);
    }

    @Override
    public CloseEventReceiptDTO closeEvent(int eventId, String winningOption) {
        try {
            // fetch the event
            Event event = eventManager.getEvent(eventId);

            // close the event and get the results
            CloseMarketResult res = event.resolveEvent(winningOption);

            // pay the market maker their commission + leftover subsidy
            String mmName = event.getMarketMaker();
            User marketMaker = userManager.getUser(mmName);
            if (marketMaker != null) {
                marketMaker.addFunds(res.commissionTaken() + res.leftoverSubsidy());
            }

            // pay the winners
            // calculates how much each winning share is worth after commission
            int totalWinningShares = event.getOptionShares(winningOption);
            double payoutPerShare = totalWinningShares > 0 ? (res.totalPayout() / totalWinningShares) : 0.0;

            // loop through the event ledger to find who holds the winning option
            Map<String, Map<String, Integer>> ledger = event.getParticipantsHoldings();
            for (Map.Entry<String, Map<String, Integer>> entry : ledger.entrySet()) {
                String username = entry.getKey();
                int userWinningShares = entry.getValue().getOrDefault(winningOption, 0);

                if (userWinningShares > 0) {
                    User winner = userManager.getUser(username);
                    if (winner != null) {
                        winner.addFunds(userWinningShares * payoutPerShare);
                    }
                }
            }


            // returns the successful receipt
            return new CloseEventReceiptDTO(
                    true,
                    "Event closed successfully",
                    res.winningOption(),
                    res.totalPayout(),
                    res.commissionTaken(),
                    res.leftoverSubsidy()
            );

        } catch (IllegalStateException e) {
            return new CloseEventReceiptDTO(
                    false,
                    e.getMessage(),
                    null,
                    0,
                    0,
                    0
            );
        }
    }

    public List<UserSummaryDTO> getAllUsers() {
        return this.userManager.getAllUsers().stream()
                .map(user -> new UserSummaryDTO(user.getName(), user.getBalance()))
                .toList();
    }

    public UserDetailsDTO getUserDetails(String username) {
        User user = this.userManager.getUser(username);
        if (user == null) {
            throw new IllegalArgumentException("User" + username + " not found");
        }
        List<PortfolioItemDTO> portfolio = new ArrayList<>();
        if (user.getPortfolio() != null) {
            for (Map.Entry<Integer, Map<String, Integer>> eventEntry : user.getPortfolio().entrySet()) {
                int eventId = eventEntry.getKey();

                Event event = this.eventManager.getEvent(eventId);
                String eventName = (event != null) ? event.getName() : "Unknown Event";

                for (Map.Entry<String, Integer> optionEntry : eventEntry.getValue().entrySet()) {
                    String optionName = optionEntry.getKey();
                    int shares = optionEntry.getValue();

                    // Only add to the UI if they actually own shares (amount > 0)
                    if (shares > 0) {
                        portfolio.add(new PortfolioItemDTO(eventName, optionName, shares));
                    }
                }
            }
        }
        return new UserDetailsDTO(user.getName(), user.getBalance(), portfolio);
    }

    @Override
    public UserEventOverviewDTO getUserEventOverview(String username, int eventId) {
        User user = userManager.getUser(username);
        Event event = eventManager.getEvent(eventId);

        double commission = user.getCommissionPaidForEvent(eventId);
        double payout = user.getPayoutForEvent(eventId);

        // calculates total investment for this event
        double totalInvestment = 0.0;
        Map<String, Double> investments = user.getOptionInvestments().getOrDefault(eventId, new java.util.HashMap<>());
        for (double amount : investments.values()) {
            totalInvestment += amount;
        }

        // calculates profit/loss (Payout received minus total money spent)
        double profitLoss = payout - totalInvestment;

        // find the winner if closed
        String winner = (event.getStatus() == EventStatus.CLOSED) ? event.getWinningOption() : null;

        List<TradeRecordDTO> history = new java.util.ArrayList<>();

        return new UserEventOverviewDTO(commission, investments, profitLoss, history, winner);
    }

    @Override
    public boolean isUserMarketMaker(String username, int eventId) {
        if (username == null) return false;
        User user = this.userManager.getUser(username);
        return user != null && user.isMarketMakerFor(eventId); // Using the method you already wrote!
    }

    @Override
    public void loadState(String filePath) {
        // דורסים את המנהל הישן בזה שנטען מהקובץ
        this.eventManager = FileHandler.loadState(filePath);
    }

    @Override
    public void saveState(String filePath) {
        // המנוע פשוט מעביר את ה-manager שלו ל-FileHandler
        FileHandler.saveState(this.eventManager, filePath);
    }
}
