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
                event.setMarketMaker(marketMaker.getName());
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
                    .map(Engine::toSummary)
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
                    list.add(toSummary(event));
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
            // newest first (the price graph reads it backwards to go left-to-right in time)
            List<TradeRecordDTO> historyDTOs = event.getTradeHistory().reversed().stream()
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
                    accountBalance(event),
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
                    .map(Engine::toSummary)
                    .toList();

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
            if (initiatingUser == null || event == null) {
                return new TradeReceiptDTO(false, initiatingUser == null ? "Unknown user." : "Unknown event.", 0, 0);
            }
            // names are case-insensitive; the order book and the ledger always use the user's own spelling
            username = initiatingUser.getName();

            // check if the user went into negative balance, if yes then he is blocked and thus can't trade.
            if (initiatingUser.isBlocked()) {
                return new TradeReceiptDTO(false, String.format(
                        "Your account is blocked because your balance is negative ($%.2f). "
                                + "Deposit funds to bring it back to $0.00 or more.", initiatingUser.getBalance()), 0, 0);
            }

            // a seller can only offer shares they own and haven't already offered in another waiting sell order
            if (action == ActionType.SELL && event.getStatus() == EventStatus.OPEN
                    && event.getTradingMethod() instanceof OrderBookTradingMethod ob
                    && event.getOptions().contains(optionName)) {
                int owned = initiatingUser.getPortfolio()
                        .getOrDefault(eventName, Map.of())
                        .getOrDefault(optionName, 0);
                int alreadyOffered = ob.getPendingSellAmount(username, optionName);
                int available = owned - alreadyOffered;
                if (amount > available) {
                    String message = alreadyOffered > 0
                            ? String.format("You can sell at most %d '%s' shares (you own %d, %d are already in your waiting sell orders).",
                                    Math.max(available, 0), optionName, owned, alreadyOffered)
                            : String.format("You can sell at most %d '%s' shares (that's how many you own).", owned, optionName);
                    return new TradeReceiptDTO(false, message, 0, 0);
                }
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
                        // totalCost includes the on-purchase commission, the per-share price doesn't
                        double pricePerShare = (res.totalCost() - res.commissionPaid()) / res.sharesProcessed();
                        String commissionNote = res.commissionPaid() > 0
                                ? String.format(" (incl. $%.2f commission)", res.commissionPaid()) : "";
                        involvedUser.deductFunds(res.totalCost(), BalanceChangeType.BUY,
                                String.format("Bought %d '%s' in '%s' @ $%.2f%s",
                                        res.sharesProcessed(), res.optionName(), eventName, pricePerShare, commissionNote));
                        involvedUser.addShares(eventName, res.optionName(), res.sharesProcessed());
                        event.recordParticipant(res.username(), res.optionName(), res.sharesProcessed());

                        // Track Portfolio Data
                        involvedUser.addCommissionPaid(eventName, res.commissionPaid());
                        involvedUser.addOptionInvestment(eventName, res.optionName(), res.totalCost());

                    } else if (res.action() == ActionType.SELL) {
                        involvedUser.addFunds(res.totalCost(), BalanceChangeType.SELL,
                                String.format("Sold %d '%s' in '%s' @ $%.2f",
                                        res.sharesProcessed(), res.optionName(), eventName, res.totalCost() / res.sharesProcessed()));
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
                        marketMaker.addFunds(res.commissionPaid(), BalanceChangeType.COMMISSION,
                                String.format("Commission from %s's purchase in '%s'", res.username(), eventName));
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

            String notAllowed = checkMarketMaker(user, event, "start");
            if (notAllowed != null) {
                return new TradeReceiptDTO(false, notAllowed, 0, 0);
            }

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
            String startDetails = method instanceof OrderBookTradingMethod
                    ? String.format("Opened '%s' (bought %d initial shares of each option)", eventName, sharesToGive)
                    : String.format("Opened '%s' (LMSR subsidy)", eventName);
            user.deductFunds(cost, BalanceChangeType.EVENT_START, startDetails);
            event.openEvent();
            event.setVersion(eventManager.incrementAndGetGlobalVersion());

            // granting the market maker the initial order book shares for EVERY option
            if (method instanceof OrderBookTradingMethod && sharesToGive > 0) {
                for (String optionName : event.getOptions()) {
                    user.addShares(eventName, optionName, sharesToGive);
                    event.recordParticipant(user.getName(), optionName, sharesToGive);
                }
            }
            return new TradeReceiptDTO(true, "Event started successfully!", cost, 0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CloseEventReceiptDTO closeEvent(String username, String eventName, String winningOption) {
        lock.writeLock().lock();
        try {
            // fetch the event
            Event event = eventManager.getEvent(eventName);

            String notAllowed = checkMarketMaker(userManager.getUser(username), event, "close");
            if (notAllowed != null) {
                return new CloseEventReceiptDTO(false, notAllowed, null, 0, 0, 0);
            }

            // close the event and get the results
            CloseMarketResult res = event.resolveEvent(winningOption);
            event.setVersion(eventManager.incrementAndGetGlobalVersion());

            // pay the market maker their commission + leftover subsidy (two rows in their balance history)
            String mmName = event.getMarketMaker();
            User marketMaker = userManager.getUser(mmName);
            if (marketMaker != null) {
                marketMaker.addFunds(res.commissionTaken(), BalanceChangeType.COMMISSION,
                        String.format("Close commission from '%s'", eventName));
                marketMaker.addFunds(res.leftoverSubsidy(), BalanceChangeType.POOL_RETURN,
                        String.format("Money left in the pool of '%s'", eventName));
                marketMaker.addPayoutReceived(eventName, res.commissionTaken() + res.leftoverSubsidy());
            }

            // pay the winners
            // calculates how much each winning share is worth after commission
            int totalWinningShares = event.getOptionShares(winningOption);
            double payoutPerShare = totalWinningShares > 0 ? (res.netPayoutToWinners() / totalWinningShares) : 0.0;

            double commissionPerShare = totalWinningShares > 0 ? (res.commissionTaken() / totalWinningShares) : 0.0;

            // loop through the event ledger to find who holds the winning option
            Map<String, Map<String, Integer>> ledger = event.getParticipantsHoldings();
            for (Map.Entry<String, Map<String, Integer>> entry : ledger.entrySet()) {
                String holderName = entry.getKey();
                int userWinningShares = entry.getValue().getOrDefault(winningOption, 0);

                if (userWinningShares > 0) {
                    User winner = userManager.getUser(holderName);
                    if (winner != null) {
                        double userPayout = userWinningShares * payoutPerShare;
                        double userCommission = userWinningShares * commissionPerShare;
                        String commissionNote = userCommission > 0
                                ? String.format(" (after $%.2f commission)", userCommission) : "";
                        winner.addFunds(userPayout, BalanceChangeType.PAYOUT,
                                String.format("Won with %d '%s' shares in '%s'%s",
                                        userWinningShares, winningOption, eventName, commissionNote));
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
            // newest first, so the latest change is at the top of the table
            List<BalanceHistoryItemDTO> balanceHistory = user.getBalanceHistory().reversed().stream()
                    .map(change -> new BalanceHistoryItemDTO(
                            change.type(),
                            change.amount(),
                            change.balanceAfter(),
                            change.timestamp(),
                            change.details()
                    )).toList();

            return new UserDetailsDTO(user.getName(), user.getBalance(), portfolio, balanceHistory, user.isBlocked());
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
    public String login(String username) {
        lock.writeLock().lock();
        try {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("User name can't be empty.");
            }
            if (userManager.isLoggedIn(username)) {
                User existing = userManager.getUser(username);
                throw new IllegalArgumentException("The user name '" + existing.getName()
                        + "' is already logged in. Please choose a different name.");
            }
            // a new name creates the user; a name that logged out before gets its account back
            userManager.addIfNotExists(username);
            userManager.setLoggedIn(username, true);
            return userManager.getUser(username).getName();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void logout(String username) {
        lock.writeLock().lock();
        try {
            if (username != null) {
                userManager.setLoggedIn(username, false);
            }
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
            double cents = toValidAmount(amount);
            user.addFunds(cents, BalanceChangeType.DEPOSIT, "Deposit");
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
            double withdrawn = Math.min(cents, user.getBalance());
            user.deductFunds(withdrawn, BalanceChangeType.WITHDRAW, "Withdrawal");
            return user.getBalance();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // the event list's view of an event (used by the full list, the active list and delta fetching)
    private static EventSummaryDTO toSummary(Event event) {
        return new EventSummaryDTO(
                event.getName(),
                event.getDescription(),
                event.getStatus().name(), // Converts the EventStatus enum to a String
                event.getCommissionPercent(),
                event.getCommissionType().name(),
                event.getOptions(),
                event.getEventTradingMethodType().name(),
                event.getMarketMaker(),
                accountBalance(event)
        );
    }

    // money in the event's own account. Before the start nothing was paid in yet (LMSR would still compute
    // its subsidy formula), and closing pays everything out (the pool value itself isn't cleared), so both are 0
    private static double accountBalance(Event event) {
        return event.getStatus() == EventStatus.OPEN ? event.getPoolValue() : 0.0;
    }

    // only the user who uploaded an event (its market maker) may start or close it; returns the reason if not, else null
    private static String checkMarketMaker(User user, Event event, String verb) {
        if (event == null) {
            return "Unknown event.";
        }
        if (user == null || !user.getName().equals(event.getMarketMaker())) {
            return "Only the event's market maker (" + event.getMarketMaker() + ") can " + verb + " it.";
        }
        return null;
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

            // newest first
            List<TradeRecordDTO> history = event.getTradeHistory().reversed().stream()
                    .filter(trade -> trade.username().equals(user.getName()))
                    .map(trade -> new TradeRecordDTO(
                            trade.action(),
                            trade.optionName(),
                            trade.sharesProcessed(),
                            trade.totalCost()
                    ))
                    .toList();

            // total shares of each option in the whole event, in the event's option order
            Map<String, Integer> optionTotals = new LinkedHashMap<>();
            for (String optionName : event.getOptions()) {
                optionTotals.put(optionName, event.getOptionShares(optionName));
            }

            return new UserEventOverviewDTO(commission, investments, profitLoss, history, winner, optionTotals);
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