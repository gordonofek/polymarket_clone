package engine;

import dto.*;
import internals.*;

import engine.files.FileHandler;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Engine implements EngineInterface {

    // כאן המנוע שומר את כל האירועים הפעילים והסגורים (למשל במפה לפי ID)
    private EventManager eventManager;
    private UserManager userManager;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Engine() {
        this.eventManager = new EventManager();
        this.userManager = new UserManager();
    }

    @Override
    public void loadDataFromXML(InputStream fileContent, String uploader) throws Exception {
        lock.writeLock().lock();
        try {
            User marketMaker = userManager.getUser(uploader);
            if (marketMaker == null) {
                throw new IllegalArgumentException("Unknown user '" + uploader + "'. Only logged in users can upload files.");
            }

            Set<String> names = eventManager.getAllEventNames();
            List<Event> events = XMLDataLoader.loadData(fileContent, names);

            // the uploader manages every event in the file (only they can start and close them)
            for (Event event : events) {
                event.setMarketMaker(uploader);
                marketMaker.addManagedEvent(event.getName());
            }
            eventManager.addEventList(events);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isDataLoaded() {
        lock.readLock().lock();
        try {
            return !eventManager.isRegistryEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<EventSummaryDTO> getAllEvents() {
        lock.readLock().lock();
        try {
            // using stream to turn all the Events into EventSummaryDTO,
            // and then putting it in a list and returning it
            return eventManager.getAllEvents().stream()
                    .map(event -> new EventSummaryDTO(
                            event.getName(),
                            event.getDescription(),
                            event.getStatus().name(), // Converts the EventStatus enum to a String
                            event.getCommissionPercent(),
                            event.getCommissionType().name(),
                            event.getOptions(),
                            event.getEventTradingMethodType().name(),
                            event.getMarketMaker()
                    ))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<EventSummaryDTO> getActiveEvents() {
        lock.readLock().lock();
        try {
            // using stream to turn all active Events into EventSummaryDTO,
            // and then putting it in a list and returning it
            List<EventSummaryDTO> list = new ArrayList<>();

            for (Event event : eventManager.getAllEvents()) {
                // Only process the event if its status is OPEN
                if (event.getStatus() == EventStatus.OPEN) {
                    EventSummaryDTO dto = new EventSummaryDTO(
                            event.getName(),
                            event.getDescription(),
                            event.getStatus().name(), // Converts the EventStatus enum to a String
                            event.getCommissionPercent(),
                            event.getCommissionType().name(),
                            event.getOptions(),
                            event.getEventTradingMethodType().name(),
                            event.getMarketMaker()
                    );
                    list.add(dto);
                }
            }

            return list;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public EventDetailsDTO getEventState(String eventName) {
        lock.readLock().lock();
        try {
            // 1. Fetch the raw domain object from your data structure
            Event event = eventManager.getEvent(eventName);

            // 2. Stream to calculate the total commission from the history
            double totalCommission = event.getTradeHistory().stream()
                    .mapToDouble(trade -> trade.commissionPaid()) // assuming TradeResult has a commission() method
                    .sum();

            // 3. Stream to build the List<OptionDetailsDTO>
            List<OptionDetailsDTO> optionsDTOs = event.getOptions().stream()
                    .map(optionName -> {
                        double currentPrice = event.getOptionPrice(optionName);
                        int sharesBought = event.getOptionShares(optionName); // Matches your DTO field name

                        double bid = 0.0, ask = 0.0, mid = 0.0, spread = 0.0;
                        List<PendingRequestDTO> buyQueue = new ArrayList<>();
                        List<PendingRequestDTO> sellQueue = new ArrayList<>();

                        TradingMethods method = event.getTradingMethod();

                        if (method instanceof OrderBookTradingMethod obMethod) {
                            bid = obMethod.getHighestBid(optionName);
                            ask = obMethod.getLowestAsk(optionName);
                            mid = obMethod.getMidPrice(optionName);
                            spread = obMethod.getSpread(optionName);

                            // Extract Buy Requests
                            var rawBuyRequests = obMethod.get_option_buy_requests(optionName);
                            for (var entry : rawBuyRequests.entrySet()) {
                                for (TradeRequest req : entry.getValue()) {
                                    buyQueue.add(new PendingRequestDTO(req.getUsername(), req.getAmount(), req.getLimitPrice(), "BUY"));
                                }
                            }

                            // Extract Sell Requests
                            var rawSellRequests = obMethod.get_option_sell_requests(optionName);
                            for (var entry : rawSellRequests.entrySet()) {
                                for (TradeRequest req : entry.getValue()) {
                                    sellQueue.add(new PendingRequestDTO(req.getUsername(), req.getAmount(), req.getLimitPrice(), "SELL"));
                                }
                            }
                        }

                        // Return using the exact order of your OptionDetailsDTO fields
                        return new OptionDetailsDTO(
                                optionName, currentPrice, sharesBought,
                                bid, ask, mid, spread,
                                buyQueue, sellQueue
                        );
                    })
                    .toList();

            // 4. Stream to map the raw trade history to TradeRecordDTOs
            List<TradeRecordDTO> historyDTOs = event.getTradeHistory().stream()
                    .map(trade -> new TradeRecordDTO(
                            trade.action(),
                            trade.optionName(),
                            trade.sharesProcessed(),
                            trade.totalCost()
                    ))
                    .toList();

            // 5. Figure out the winning option (null if not closed)
            String winner = (event.getStatus() == EventStatus.CLOSED) ? event.getWinningOption() : null;

            // 6. Finally, return the new object using the constructor
            return new EventDetailsDTO(
                    event.getName(),
                    event.getStatus().name(),
                    event.getPoolValue(),
                    totalCommission,
                    optionsDTOs,
                    historyDTOs,
                    winner
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    public EventsDeltaDTO getEventsDelta(int clientVersion) {
        lock.readLock().lock();
        try {
            // שולפים רק אירועים שהשתנו, וממירים אותם ל-DTO
            List<EventSummaryDTO> updatedEvents = eventManager.getEventsSinceVersion(clientVersion).stream()
                    .map(event -> new EventSummaryDTO(
                            event.getName(),
                            event.getDescription(),
                            event.getStatus().name(),
                            event.getCommissionPercent(),
                            event.getCommissionType().name(),
                            event.getOptions(),
                            event.getEventTradingMethodType().name(),
                            event.getMarketMaker()
                    )).toList();

            // אורזים יחד עם השעון העדכני
            return new EventsDeltaDTO(eventManager.getGlobalVersion(), updatedEvents);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<ParticipantHoldingDTO> getEventParticipants(String eventName) {
        lock.readLock().lock();
        try {
            Event event = eventManager.getEvent(eventName);
            Map<String, Map<String, Integer>> ledger = event.getParticipantsHoldings();

            List<ParticipantHoldingDTO> holdings = ledger.entrySet().stream()
                    .map(entry -> {
                                String participantName = entry.getKey();
                                Map<String, Integer> shares = new HashMap<>(entry.getValue());
                                return new ParticipantHoldingDTO(participantName, shares);
                            }
                    ).toList();

            return holdings;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public TradeReceiptDTO placeOrder(String username, String eventName, String optionName, int amount, ActionType action, double limitPrice) {
        lock.writeLock().lock();
        try {
            Event event = eventManager.getEvent(eventName);
            User initiatingUser = userManager.getUser(username);

            // check if the user went into negative balance, if yes then he is blocked and thus can't trade.
            if (initiatingUser.isBlocked()) {
                return new TradeReceiptDTO(false, "User is blocked due to negative balance.", 0, 0);
            }

            // 1. Capture the LIST of results
            List<TradeResult> results = event.processTrade(new TradeRequest(action, optionName, amount, limitPrice, username));

            event.setVersion(eventManager.incrementAndGetGlobalVersion());

            double totalCostForInitiator = 0.0;
            double totalCommissionForInitiator = 0.0;
            boolean overallSuccess = false;
            String finalMessage = "Order processed";

            // 2. Iterate through every receipt generated by this action
            for (TradeResult res : results) {

                if (!res.isSuccess()) {
                    finalMessage = res.message();
                    continue;
                }

                // Skip the "routed" placeholder messages (which have 0 shares) so we don't do empty math
                if (res.sharesProcessed() == 0) {
                    finalMessage = res.message();
                    overallSuccess = true;
                    continue;
                }

                overallSuccess = true;

                // 3. Fetch the specific user for THIS receipt (could be the buyer or the seller!)
                // Make sure your TradeResult uses orderId() or username() depending on your refactor!
                User involvedUser = userManager.getUser(res.username());

                // 4. Apply the financial changes AND update the Portfolio tracking maps
                if (involvedUser != null)
                {
                    if (res.action() == ActionType.BUY) {
                        involvedUser.deductFunds(res.totalCost());
                        involvedUser.addShares(eventName, res.optionName(), res.sharesProcessed());
                        event.recordParticipant(res.username(), res.optionName(), res.sharesProcessed());

                        // Track Portfolio Data
                        involvedUser.addCommissionPaid(eventName, res.commissionPaid());
                        involvedUser.addOptionInvestment(eventName, res.optionName(), res.totalCost());

                    } else if (res.action() == ActionType.SELL) {
                        involvedUser.addFunds(res.totalCost());
                        involvedUser.addShares(eventName, res.optionName(), -res.sharesProcessed());
                        event.recordParticipant(res.username(), res.optionName(), -res.sharesProcessed());

                        // Track Portfolio Data
                        involvedUser.addOptionInvestment(eventName, res.optionName(), -res.totalCost());
                    }

                    // 5. Accumulate the costs/commissions ONLY for the user who initiated the request
                    if (res.username().equals(username)) {
                        totalCostForInitiator += res.totalCost();
                        totalCommissionForInitiator += res.commissionPaid();
                        finalMessage = res.message();
                    }
                }

                // Pay the Market Maker any ON_PURCHASE commissions
                if (res.commissionPaid() > 0) {
                    User marketMaker = userManager.getUser(event.getMarketMaker());
                    if (marketMaker != null) {
                        marketMaker.addFunds(res.commissionPaid());
                    }
                }
            }
            // 6. Return the summary DTO
            return new TradeReceiptDTO(overallSuccess, finalMessage, totalCostForInitiator, totalCommissionForInitiator);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public TradeReceiptDTO startEvent(String username, String eventName) {
        lock.writeLock().lock();
        try {
            Event event = eventManager.getEvent(eventName);
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
            event.setVersion(eventManager.incrementAndGetGlobalVersion());

            // granting the market maker the initial order book shares for EVERY option
            if (method instanceof OrderBookTradingMethod && sharesToGive > 0) {
                for (String optionName : event.getOptions()) {
                    user.addShares(eventName, optionName, sharesToGive);
                    event.recordParticipant(username, optionName, sharesToGive);
                }
            }
            return new TradeReceiptDTO(true, "Event started successfully!", cost, 0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CloseEventReceiptDTO closeEvent(String eventName, String winningOption) {
        lock.writeLock().lock();
        try {
            // fetch the event
            Event event = eventManager.getEvent(eventName);

            // close the event and get the results
            CloseMarketResult res = event.resolveEvent(winningOption);
            event.setVersion(eventManager.incrementAndGetGlobalVersion());

            // pay the market maker their commission + leftover subsidy
            String mmName = event.getMarketMaker();
            User marketMaker = userManager.getUser(mmName);
            if (marketMaker != null) {
                double mmPayout = res.commissionTaken() + res.leftoverSubsidy();
                marketMaker.addFunds(mmPayout);
                marketMaker.addPayoutReceived(eventName, mmPayout);
            }

            // pay the winners
            // calculates how much each winning share is worth after commission
            int totalWinningShares = event.getOptionShares(winningOption);
            double payoutPerShare = totalWinningShares > 0 ? (res.netPayoutToWinners() / totalWinningShares) : 0.0;

            double commissionPerShare = totalWinningShares > 0 ? (res.commissionTaken() / totalWinningShares) : 0.0;

            // loop through the event ledger to find who holds the winning option
            Map<String, Map<String, Integer>> ledger = event.getParticipantsHoldings();
            for (Map.Entry<String, Map<String, Integer>> entry : ledger.entrySet()) {
                String username = entry.getKey();
                int userWinningShares = entry.getValue().getOrDefault(winningOption, 0);

                if (userWinningShares > 0) {
                    User winner = userManager.getUser(username);
                    if (winner != null) {
                        double userPayout = userWinningShares * payoutPerShare;
                        double userCommission = userWinningShares * commissionPerShare;
                        winner.addFunds(userPayout);
                        winner.addPayoutReceived(eventName, userPayout);
                        winner.addCommissionPaid(eventName, userCommission);
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
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<UserSummaryDTO> getAllUsers() {
        lock.readLock().lock();
        try {
            return this.userManager.getAllUsers().stream()
                    .map(user -> new UserSummaryDTO(user.getName(), user.getBalance()))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public UserDetailsDTO getUserDetails(String username) {
        lock.readLock().lock();
        try {
            User user = this.userManager.getUser(username);
            if (user == null) {
                throw new IllegalArgumentException("User" + username + " not found");
            }
            List<PortfolioItemDTO> portfolio = new ArrayList<>();
            if (user.getPortfolio() != null) {
                for (Map.Entry<String, Map<String, Integer>> eventEntry : user.getPortfolio().entrySet()) {
                    String eventName = eventEntry.getKey();

                    Event event = this.eventManager.getEvent(eventName);

                    for (Map.Entry<String, Integer> optionEntry : eventEntry.getValue().entrySet()) {
                        String optionName = optionEntry.getKey();
                        int shares = optionEntry.getValue();

                        // Only add to the UI if they actually own shares (amount > 0)
                        if (shares > 0) {
                            String statusStr = (event != null) ? event.getStatus().name() : "UNKNOWN";

                            // Calculate Profil/Loss if closed, or show 0.0 if still open
                            double profitLoss = 0.0;
                            if (event != null && event.getStatus() == EventStatus.CLOSED) {
                                UserEventOverviewDTO overview = getUserEventOverview(user.getName(), eventName);
                                profitLoss = overview.profitLoss();
                            }

                            portfolio.add(new PortfolioItemDTO(eventName, optionName, shares, statusStr, profitLoss));
                        }
                    }
                }
            }
            return new UserDetailsDTO(user.getName(), user.getBalance(), portfolio);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean addUser(String username) {
        lock.writeLock().lock();
        try {
            return this.userManager.addIfNotExists(username);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public double depositFunds(String username, double amount) {
        lock.writeLock().lock();
        try {
            User user = getExistingUser(username);
            // addFunds also unblocks a user whose balance gets back to 0 or more
            user.addFunds(toValidAmount(amount));
            return user.getBalance();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public double withdrawFunds(String username, double amount) {
        lock.writeLock().lock();
        try {
            User user = getExistingUser(username);
            double cents = toValidAmount(amount);
            // deductFunds allows going negative (for trades), a withdrawal must not
            if (cents > roundToCents(user.getBalance())) {
                throw new IllegalArgumentException(String.format(
                        "Insufficient funds: you have $%.2f.", user.getBalance()));
            }
            user.deductFunds(Math.min(cents, user.getBalance()));
            return user.getBalance();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private User getExistingUser(String username) {
        User user = userManager.getUser(username);
        if (user == null) {
            throw new IllegalArgumentException("Unknown user '" + username + "'.");
        }
        return user;
    }

    // money moves in whole cents; rejects NaN, infinity, zero and negative amounts
    private static double toValidAmount(double amount) {
        double cents = roundToCents(amount);
        if (!Double.isFinite(amount) || cents <= 0) {
            throw new IllegalArgumentException("The amount must be a positive number.");
        }
        return cents;
    }

    private static double roundToCents(double amount) {
        return Math.round(amount * 100) / 100.0;
    }

    @Override
    public UserEventOverviewDTO getUserEventOverview(String username, String eventName) {
        lock.readLock().lock();
        try {
            User user = userManager.getUser(username);
            Event event = eventManager.getEvent(eventName);

            double commission = user.getCommissionPaidForEvent(eventName);
            double payout = user.getPayoutForEvent(eventName);

            // calculates total investment for this event
            double totalInvestment = 0.0;
            Map<String, Double> investments = user.getOptionInvestments().getOrDefault(eventName, new java.util.HashMap<>());
            for (double amount : investments.values()) {
                totalInvestment += amount;
            }

            // calculates profit/loss (Payout received minus total money spent)
            double profitLoss = payout - totalInvestment;

            // find the winner if closed
            String winner = (event.getStatus() == EventStatus.CLOSED) ? event.getWinningOption() : null;

            List<TradeRecordDTO> history = event.getTradeHistory().stream()
                    .filter(trade -> trade.username().equals(username))
                    .map(trade -> new TradeRecordDTO(
                            trade.action(),
                            trade.optionName(),
                            trade.sharesProcessed(),
                            trade.totalCost()
                    ))
                    .toList();

            return new UserEventOverviewDTO(commission, investments, profitLoss, history, winner);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isUserMarketMaker(String username, String eventName) {
        lock.readLock().lock();
        try {
            if (username == null) return false;
            User user = this.userManager.getUser(username);
            return user != null && user.isMarketMakerFor(eventName); // Using the method you already wrote!
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void loadState(String filePath) {
        lock.writeLock().lock();
        try {
            // דורסים את המנהל הישן בזה שנטען מהקובץ
            this.eventManager = FileHandler.loadState(filePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void saveState(String filePath) {
        lock.readLock().lock();
        try {
            // המנוע פשוט מעביר את ה-manager שלו ל-FileHandler
            FileHandler.saveState(this.eventManager, filePath);
        } finally {
            lock.readLock().unlock();
        }
    }
}