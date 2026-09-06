package engine;
import internals.*;

import engine.dto.*;
import engine.files.FileHandler;

import java.util.ArrayList;
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
        List<Event> loadedEvents = loader.loadEventsFromXML(filePath);

        // auto opens all the events.
        for (Event event : loadedEvents) {
            event.openEvent();
        }

        eventManager.clearEvents();
        eventManager.addEventList(loadedEvents);
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
    public TradeReceiptDTO placeOrder(String username, int eventId, String optionName, int amount, ActionType action, double limitPrice) {
        Event event = eventManager.getEvent(eventId);
        User user = userManager.getUser(username);

        // check if the user went into negative balance, if yes then he is blocked and thus can't trade.
        if (user.isBlocked()) {
            return new TradeReceiptDTO(false, "User is blocked due to negative balance.", 0, 0);
        }

        TradeResult res = event.processTrade(new TradeRequest(action,optionName,amount,limitPrice,username));

        if (res.isSuccess()) {
            if (action == ActionType.BUY) {
                user.deductFunds(res.totalCost());
                user.addShares(eventId, optionName, res.sharesProcessed());
                event.recordParticipant(username, optionName, res.sharesProcessed());
            } else if (action == ActionType.SELL) {
                user.addFunds(res.totalCost());
                user.addShares(eventId, optionName, -res.sharesProcessed());
                event.recordParticipant(username, optionName, -res.sharesProcessed());
            }
        }
        return new TradeReceiptDTO(res.isSuccess(),res.message(), res.totalCost(), res.commissionPaid());
    }

    @Override
    public CloseEventReceiptDTO closeEvent(int eventId, String winningOption) {
        try {
            // 1. שולפים את האירוע מהמערכת
            Event event = eventManager.getEvent(eventId);

            // 2. סוגרים את האירוע ומקבלים את תוצאות החישוב
            CloseMarketResult res = event.resolveEvent(winningOption);

            // 3. אם הגענו לכאן בלי שגיאות, הסגירה הצליחה! נחזיר קבלה חיובית מלאה בנתונים
            return new CloseEventReceiptDTO(
                    true,
                    "Event closed successfully",
                    res.winningOption(),
                    res.totalPayout(),
                    res.commissionTaken(),
                    res.leftoverSubsidy()
            );

        } catch (IllegalStateException e) {
            // במקרה שהאירוע לא היה פתוח (וכבר נסגר או טרם התחיל), נחזיר קבלה עם הודעת שגיאה
            return new CloseEventReceiptDTO(
                    false,
                    e.getMessage(), // מחזיר את ההודעה מתוך ה-Exception שיצרת ב-MarketEvent
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
        if (user == null)
        {
            throw new IllegalArgumentException("User" + username + " not found");
        }
        List<PortfolioItemDTO> portfolio =  new ArrayList<>();
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
        return new UserDetailsDTO(user.getName(), user.getBalance(), portfolio);
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
