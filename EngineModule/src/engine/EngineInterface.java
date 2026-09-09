package engine;

import engine.dto.*;
import internals.ActionType;

import java.util.List;

public interface EngineInterface {

    // ==========================================
    // הגדרות וטעינה
    // ==========================================

    /**
     * טוען נתונים מקובץ XML ומרסט את המערכת.
     */
    void loadDataFromXML(String filePath) throws Exception;

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
    EventDetailsDTO getEventState(int eventId);

    /**
     * returns the details of the user
     */
    UserDetailsDTO getUserDetails(String username);

    /**
     *  returns the participants information for a specific event
     */
    List<ParticipantHoldingDTO> getEventParticipants(int eventId);

    /**
     * returns all the users
     */
    List<UserSummaryDTO> getAllUsers();

    /**
     *  checks if the user is the market maker for this event
     */
    boolean isUserMarketMaker(String username, int eventId);

    // ==========================================
    // ביצוע פעולות (Commands)
    // ==========================================

    /**
     * מבצע פקודת מסחר (קנייה או מכירה) ומחזיר קבלה עם התוצאה (פקודה 4).
     */
    TradeReceiptDTO placeOrder(String username, int eventId, String optionName, int amount, ActionType action, double limitPrice);

    /**
     * סוגר אירוע פעיל, מכריז על מנצח ומחזיר את נתוני הסגירה (פקודה 5).
     */
    CloseEventReceiptDTO closeEvent(int eventId, String winningOption);

    // saves the current state of the program to bin file
    void saveState(String filePath);

    // loads program state from a bin file
    void loadState(String filePath);
}