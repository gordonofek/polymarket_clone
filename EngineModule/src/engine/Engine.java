package engine;
import internals.*;

import engine.dto.*;
import engine.files.FileHandler;

import java.util.ArrayList;
import java.util.List;

public class Engine implements EngineInterface {

    // כאן המנוע שומר את כל האירועים הפעילים והסגורים (למשל במפה לפי ID)
    private EventManager manager;

    public Engine() {
        this.manager = new EventManager();
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

        manager.clearEvents();
        manager.addEventList(loadedEvents);
    }

    @Override
    public boolean isDataLoaded() {
        return !manager.isRegistryEmpty();
    }

    @Override
    public List<EventSummaryDTO> getAllEvents() {
        // using stream to turn all the Events into EventSummaryDTO,
        // and then putting it in a list and returning it
        return manager.getAllEvents().stream()
                .map(event -> new EventSummaryDTO(
                        event.getId(),
                        event.getName(),
                        event.getDescription(),
                        event.getStatus().name(), // Converts the EventStatus enum to a String
                        event.getCommissionPercent(),
                        event.getCommissionType().name(),
                        event.getOptions()
                ))
                .toList();
    }

    @Override
    public List<EventSummaryDTO> getActiveEvents() {
        // using stream to turn all active Events into EventSummaryDTO,
        // and then putting it in a list and returning it
        List<EventSummaryDTO> list = new ArrayList<>();

        for (Event event : manager.getAllEvents()) {
            // Only process the event if its status is OPEN
            if (event.getStatus() == EventStatus.OPEN) {
                EventSummaryDTO dto = new EventSummaryDTO(
                        event.getId(),
                        event.getName(),
                        event.getDescription(),
                        event.getStatus().name(), // Converts the EventStatus enum to a String
                        event.getCommissionPercent(),
                        event.getCommissionType().name(),
                        event.getOptions()
                );
                list.add(dto);
            }
        }

        return list;
    }

    @Override
    public EventDetailsDTO getEventState(int eventId) {
        // 1. Fetch the raw domain object from your data structure
        Event event = manager.getEvent(eventId);

        // 2. Cleverly use a Stream to calculate the total commission from the history
        double totalCommission = event.getTradeHistory().stream()
                .mapToDouble(trade -> trade.commissionPaid()) // assuming TradeResult has a commission() method
                .sum();

        // 3. Cleverly use a Stream to build the List<OptionDetailsDTO>
        List<OptionDetailsDTO> optionsDTOs = event.getOptions().stream()
                .map(optionName -> new OptionDetailsDTO(
                        optionName,
                        event.getOptionPrice(optionName),
                        event.getOptionShares(optionName)
                ))
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
    public TradeReceiptDTO placeOrder(int eventId, String optionName, int amount, ActionType action, double limitPrice) {
        Event event = manager.getEvent(eventId);
        TradeResult res = event.processTrade(new TradeRequest(action,optionName,amount,limitPrice));
        return new TradeReceiptDTO(res.isSuccess(),res.message(), res.totalCost(), res.commissionPaid());
    }

    @Override
    public CloseEventReceiptDTO closeEvent(int eventId, String winningOption) {
        try {
            // 1. שולפים את האירוע מהמערכת
            Event event = manager.getEvent(eventId);

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

    @Override
    public void loadState(String filePath) {
        // דורסים את המנהל הישן בזה שנטען מהקובץ
        this.manager = FileHandler.loadState(filePath);
    }

    @Override
    public void saveState(String filePath) {
        // המנוע פשוט מעביר את ה-manager שלו ל-FileHandler
        FileHandler.saveState(this.manager, filePath);
    }
}
