package engine;

import engine.xml.GMEvent;
import engine.xml.GuessMarket;
import internals.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import java.io.FileReader;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public class XMLDataLoader {


public static List<Event> loadData(InputStream fileContent) throws Exception {
    GuessMarket guessMarket = loadGuessMarket (fileContent);

    // 3. הפעלת שרשרת הפונקציות
    List<Event> engineEvents = parseEvents(guessMarket);

    return engineEvents;
}


public static GuessMarket loadGuessMarket (InputStream fileContent) throws Exception{
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
    SAXSource source = new SAXSource(xmlReader, new InputSource(fileContent));

    // Unmarshal GuessMarket based on xmlReader parser
    GuessMarket guessMarket = (GuessMarket) unmarshaller.unmarshal(source);

    return guessMarket;
}

private static List<Event> parseEvents(GuessMarket guessMarket) throws InvalidXMLException{
    // 2. וולידציה והמרה לאובייקטי מנוע
    List<Event> engineEvents = new ArrayList<>();

    // סט לאחסון שמות אירועים שכבר קראנו, כדי למצוא כפילויות
    Set<String> uniqueEventNames = new HashSet<>();

    // מעבר על רשימת האירועים ש-JAXB יצר
    for (GMEvent xmlEvent : guessMarket.getGMEvents().getGMEvent()) {

        // איחוד רשימת המילים של השם למחרוזת אחת
        String fullEventName = String.join(" ", xmlEvent.getName());

        // --- וולידציה (Application-wise) ---

        // --- וולידציה של שמות כפולים ---
        if (uniqueEventNames.contains(fullEventName)) {
            throw new InvalidXMLException("Duplicate event name found in XML: '" + fullEventName + "'. Event names must be unique.");
        }
        uniqueEventNames.add(fullEventName);


        // בדיקת עמלה בין 0 ל-90
        int commission = xmlEvent.getCommission().getValue();
        if (commission < 0 || commission > 90) {
            throw new InvalidXMLException("Invalid commission for event '" + fullEventName + "'. Must be between 0 and 90.");
        }

        // --- המרה (Mapping) ---

        // א. הכנת מילון האפשרויות מראש עם 0 מניות
        Map<String, Integer> optionsMap = new HashMap<>();
        for (String optionName : xmlEvent.getGMOptions().getGMOption()) {
            optionsMap.put(optionName, 0);
        }

        String typeStr = xmlEvent.getCommission().getType();
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

}