package engine;

import dto.*;


import java.io.InputStream;
import java.util.List;

public interface EngineInterface {

    // ==========================================
    // הגדרות וטעינה
    // ==========================================

    /**
     * Loads the events from an XML file and adds them to the existing ones.
     * The uploader becomes the market maker of every loaded event.
     */
    void loadDataFromXML(InputStream fileContent, String uploader) throws Exception;

    /**
     * מחזיר true אם נטענו נתונים בהצלחה והמערכת מוכנה לעבודה.
     */
    boolean isDataLoaded();


    // ==========================================
    // שליפת נתונים (Queries)
    // ==========================================

    /**
     * מחזיר רשימה מקוצרת של כל האירועים הקיימים במערכת (פקודה 2).
     */
    List<EventSummaryDTO> getAllEvents();

    /**
     * מחזיר רשימה מקוצרת של האירועים הפעילים בלבד (עבור פקודות 4 ו-5).
     */
    List<EventSummaryDTO> getActiveEvents();

    /**
     * מחזיר את כל המידע המפורט עבור אירוע ספציפי (פקודה 3).
     */
    EventDetailsDTO getEventState(String eventName);

    /**
     * returns the details of the user
     */
    UserDetailsDTO getUserDetails(String username);

    /**
     *  returns the participants information for a specific event
     */
    List<ParticipantHoldingDTO> getEventParticipants(String eventName);

    /**
     * returns all the users
     */
    List<UserSummaryDTO> getAllUsers();

    /**
     *  checks if the user is the market maker for this event
     */
    boolean isUserMarketMaker(String username, String eventName);

    // gets the user's event info for the overview
    UserEventOverviewDTO getUserEventOverview(String username, String eventName);

    // ==========================================
    // ביצוע פעולות (Commands)
    // ==========================================

    /**
     * מבצע פקודת מסחר (קנייה או מכירה) ומחזיר קבלה עם התוצאה (פקודה 4).
     */
    TradeReceiptDTO placeOrder(String username, String eventName, String optionName, int amount, ActionType action, double limitPrice);

    /**
     * סוגר אירוע פעיל, מכריז על מנצח ומחזיר את נתוני הסגירה (פקודה 5).
     */
    CloseEventReceiptDTO closeEvent(String eventName, String winningOption);

    /**
     * opens the event. checks the cost, market maker's balance, deducts the money and distributes the starting shares for Order Book events.
     */
    TradeReceiptDTO startEvent(String username, String eventName);

    /**
     * adds a new user when we login to the server(from part 3)
     */
    public boolean addUser(String username);

    // saves the current state of the program to bin file
    void saveState(String filePath);

    // loads program state from a bin file
    void loadState(String filePath);
}