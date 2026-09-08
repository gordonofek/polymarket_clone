package engine;

import engine.xml.GMEvent;
import engine.xml.GMUser;
import engine.xml.GuessMarket;
import internals.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import java.io.FileReader;

import java.io.File;
import java.util.*;

public class XMLDataLoader {


public static ParsedData loadData(String filePath) throws Exception {
    GuessMarket guessMarket = loadGuessMarket (filePath);

    // 2. יצירת מאגר ה-IDs שישמש לתקשורת בין הפונקציות
    Set<Integer> validEventIds = new HashSet<>();

    // 3. הפעלת שרשרת הפונקציות
    List<Event> engineEvents = parseEvents(guessMarket, validEventIds);
    List<User> engineUsers = parseUsers(guessMarket, validEventIds);
    validateMarketMakers(engineEvents, engineUsers);

    // 4. החזרת התוצאה הארוזה
    return new ParsedData(engineEvents, engineUsers);
}

public static GuessMarket loadGuessMarket (String filePath) throws Exception{
    // 1. Unmarshalling: המרת ה-XML לאובייקטים בעזרת JAXB
    File file = new File(filePath);
    if (!file.exists() || !file.getName().endsWith(".xml")) {
        throw new InvalidXMLException("The file does not exist or is not an XML file.");
    }

    // creates context of the structure GuessMarket
    JAXBContext context = JAXBContext.newInstance(GuessMarket.class);

    // creates unmarshaller that takes the context and create GuessMarket dto
    Unmarshaller unmarshaller = context.createUnmarshaller();

    // Create a custom XML reader factory that ignores missing namespace definitions
    SAXParserFactory spf = SAXParserFactory.newInstance();

    // we set the spf to ignore nameSpace settings that can cause problems
    spf.setNamespaceAware(false);

    // Create a custom XML reader based on the spf factory
    XMLReader xmlReader = spf.newSAXParser().getXMLReader();

    // creates a source based on the file we checked and the xmlReader we created
    SAXSource source = new SAXSource(xmlReader, new InputSource(new FileReader(file)));

    // Unmarshal GuessMarket based on xmlReader parser
    GuessMarket guessMarket = (GuessMarket) unmarshaller.unmarshal(source);

    return guessMarket;
}

private static List<Event> parseEvents(GuessMarket guessMarket, Set<Integer> validEventIds) throws InvalidXMLException{
    // 2. וולידציה והמרה לאובייקטי מנוע
    List<Event> engineEvents = new ArrayList<>();

    // מעבר על רשימת האירועים ש-JAXB יצר
    for (GMEvent xmlEvent : guessMarket.getGMEvents().getGMEvent()) {

        // איחוד רשימת המילים של השם למחרוזת אחת
        String fullEventName = String.join(" ", xmlEvent.getName());

        // --- וולידציה (Application-wise) ---

        // בדיקת ID ייחודי
        if (!validEventIds.add(xmlEvent.getId())) {
            throw new InvalidXMLException("Duplicate Event ID found: " + xmlEvent.getId());
        }

        // בדיקת עמלה בין 0 ל-90
        int commission = xmlEvent.getComision().getValue();
        if (commission < 0 || commission > 90) {
            throw new InvalidXMLException("Invalid commission for event '" + fullEventName + "'. Must be between 0 and 90.");
        }

        // --- המרה (Mapping) ---

        // א. הכנת מילון האפשרויות מראש עם 0 מניות
        Map<String, Integer> optionsMap = new HashMap<>();
        for (String optionName : xmlEvent.getGMOptions().getGMOption()) {
            optionsMap.put(optionName, 0);
        }

        String typeStr = xmlEvent.getComision().getType();
        CommissionType commissionType;
        switch (typeStr.toLowerCase()) {
            case "on-purchase":
                commissionType = CommissionType.ON_PURCHASE;
                break;
            case "on-close":
                commissionType = CommissionType.ON_CLOSE;
                break;
            default:
                // זורקים את ה-Exception המותאם אישית שלנו אם יש ערך לא מוכר
                throw new InvalidXMLException("Unknown commission type in XML: " + typeStr);
        }

        // ב. יצירת ה-Trading Method
        TradingMethods tradingMethod;
        List<String> optionsList = xmlEvent.getGMOptions().getGMOption();

        if (xmlEvent.getGMMethod().getGMLMSR() != null) {
            // זהו אירוע LMSR
            int bParameter = xmlEvent.getGMMethod().getGMLMSR().getB();
            tradingMethod = new LMSRTradingMethod(bParameter, optionsMap, commission, commissionType);

        } else if (xmlEvent.getGMMethod().getGMOrderBook() != null) {
            // זהו אירוע Order Book
            int baseValue = xmlEvent.getGMMethod().getGMOrderBook().getD();
            int initialInvestment = xmlEvent.getGMMethod().getGMOrderBook().getInitial();
            boolean allowMint = Boolean.parseBoolean(xmlEvent.getGMMethod().getGMOrderBook().getAllowMint());

            tradingMethod = new OrderBookTradingMethod(baseValue, initialInvestment, allowMint, commission, commissionType, optionsList);

        } else {
            throw new InvalidXMLException("Event '" + fullEventName + "' must have either LMSR or OrderBook method.");
        }

        // ג. יצירת האירוע האמיתי של המנוע
        Event newEvent = new MarketEvent(
                xmlEvent.getId(),
                fullEventName,
                xmlEvent.getDescription(),
                commission,
                commissionType,
                optionsList,
                tradingMethod // מעבירים את הממשק הכללי שיצרנו!
        );

        engineEvents.add(newEvent);
    }
    return engineEvents;
}

private static List<User> parseUsers(GuessMarket guessMarket, Set<Integer> validEventIds) throws InvalidXMLException{
    List<User> engineUsers = new ArrayList<>();
    Set<String> existingNames = new HashSet<>();

    // ייתכן שבקובץ ה-XML אין בכלל תגית users, אז נוודא שלא נקבל NullPointerException
    if (guessMarket.getGMUsers() == null || guessMarket.getGMUsers().getGMUser() == null) {
        return engineUsers;
    }

    // מעבר על רשימת המשתמשים מתוך ה-XML
    for (GMUser xmlUser : guessMarket.getGMUsers().getGMUser()) {

        // 1. חילוץ ושמירת השם (ננקה רווחים למקרה שיש)
        String username = xmlUser.getName().trim();

        // בדיקה שהשם ייחודי (אם add מחזיר false, השם כבר קיים ב-Set)
        if (!existingNames.add(username)) {
            throw new InvalidXMLException("Duplicate User name found: " + username);
        }

        // 2. וולידציה על היתרה ההתחלתית
        // שים לב: בדוק איך קוראים לגטר של הכסף אצלך (ייתכן שזה getFunds, getInitialCash וכו')
        double initialFunds = xmlUser.getInitialCash();
        if (initialFunds < 0) {
            throw new InvalidXMLException("User '" + username + "' has invalid initial funds. Must be positive.");
        }

        // 3. חילוץ האירועים שהמשתמש הזה מנהל (Market Maker)
        Set<Integer> managedEvents = new HashSet<>();

        // בודקים אם יש לו בכלל הגדרת Market Maker
        if (xmlUser.getGMMarketMaker() != null) {

            // עוברים על רשימת אובייקטי ה-Event שה-Market Maker מנהל
            for (engine.xml.Event xmlMmEvent : xmlUser.getGMMarketMaker().getEvent()) {

                // שולפים את ה-ID מתוך תגית ה-Event הספציפית
                int eventId = xmlMmEvent.getId();

                // הבדיקה הקריטית: האם האירוע שהוא רוצה לנהל באמת קיים ב-XML?
                if (!validEventIds.contains(eventId)) {
                    throw new InvalidXMLException("User '" + username + "' is defined as Market Maker for an unknown Event ID: " + eventId);
                }

                managedEvents.add(eventId);
            }
        }

        // 4. יצירת אובייקט ה-User של המנוע והוספתו לרשימה
        // (תצטרך לוודא שהבנאי של מחלקת User שלך מקבל את הפרמטרים האלו)
        User newUser = new User(username, initialFunds, managedEvents);
        engineUsers.add(newUser);
    }

    return engineUsers;
}

private static void validateMarketMakers(List<Event> events, List<User> users) throws InvalidXMLException {
    // find all the Events id's that have at least one market maker
    Set<Integer> allManagedEventIds = new HashSet<>();
    for (User user : users) {
        // function that adds elements to sets, avoids duplicates
        allManagedEventIds.addAll(user.getManagedEvents());
    }

   // pass over all events and see if we can find an event id that
    // is not in allManagedEventIds we found from before if yes the validation failed
    for (Event event : events) {
        if (!allManagedEventIds.contains(event.getId())) {
            throw new InvalidXMLException("Validation Error: Event '" + event.getName() + "' (ID: " + event.getId() + ") has no Market Maker defined!");
        }
    }
}

}