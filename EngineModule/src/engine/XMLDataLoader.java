package engine;

import engine.xml.GMEvent;
import engine.xml.GuessMarket;
import internals.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import java.io.FileReader;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public class XMLDataLoader {


public static List<Event> loadData(InputStream fileContent, Set<String> existingSystemEvents) throws Exception {
    GuessMarket guessMarket = loadGuessMarket (fileContent);

    // 3. הפעלת שרשרת הפונקציות
    List<Event> engineEvents = parseEvents(guessMarket, existingSystemEvents);

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
    try {
        return (GuessMarket) unmarshaller.unmarshal(source);
    } catch (UnmarshalException e) {
        // JAXB leaves its own message null, the real reason is in the linked parser exception
        Throwable reason = e.getLinkedException() != null ? e.getLinkedException() : e;
        if (reason instanceof SAXParseException parseError) {
            throw new InvalidXMLException("The file is not valid XML (line " + parseError.getLineNumber()
                    + ", column " + parseError.getColumnNumber() + "): " + parseError.getMessage());
        }
        throw new InvalidXMLException("The file is not valid XML: " + reason.getMessage());
    }
}

// an event needs at least two options, none of them blank or repeated
private static void validateOptions(String eventName, List<String> options) throws InvalidXMLException {
    if (options == null || options.size() < 2) {
        throw new InvalidXMLException("Event '" + eventName + "' must have at least 2 options.");
    }
    Set<String> seen = new HashSet<>();
    for (String option : options) {
        if (option == null || option.isBlank()) {
            throw new InvalidXMLException("Event '" + eventName + "' has an option with no name.");
        }
        if (!seen.add(option)) {
            throw new InvalidXMLException("Event '" + eventName + "' has the option '" + option + "' more than once.");
        }
    }
}

private static List<Event> parseEvents(GuessMarket guessMarket, Set<String> existingSystemEvents) throws InvalidXMLException{
    // 2. וולידציה והמרה לאובייקטי מנוע
    List<Event> engineEvents = new ArrayList<>();

    // סט לאחסון שמות אירועים שכבר קראנו, כדי למצוא כפילויות
    Set<String> uniqueEventNames = new HashSet<>();

    if (guessMarket.getGMEvents() == null || guessMarket.getGMEvents().getGMEvent().isEmpty()) {
        throw new InvalidXMLException("The file doesn't contain any events.");
    }

    // מעבר על רשימת האירועים ש-JAXB יצר
    for (GMEvent xmlEvent : guessMarket.getGMEvents().getGMEvent()) {

        // איחוד רשימת המילים של השם למחרוזת אחת
        String fullEventName = xmlEvent.getName() == null ? "" : String.join(" ", xmlEvent.getName()).trim();

        // --- וולידציה (Application-wise) ---

        // the file isn't checked against the XSD, so missing elements are caught here instead of crashing later
        if (fullEventName.isEmpty()) {
            throw new InvalidXMLException("An event in the file has no name.");
        }
        if (xmlEvent.getCommission() == null) {
            throw new InvalidXMLException("Event '" + fullEventName + "' has no commission.");
        }
        if (xmlEvent.getGMOptions() == null) {
            throw new InvalidXMLException("Event '" + fullEventName + "' has no options.");
        }
        if (xmlEvent.getGMMethod() == null) {
            throw new InvalidXMLException("Event '" + fullEventName + "' has no trading method.");
        }
        validateOptions(fullEventName, xmlEvent.getGMOptions().getGMOption());

        if (existingSystemEvents.contains(fullEventName) || uniqueEventNames.contains(fullEventName)) {
            throw new InvalidXMLException("Duplicate event name found: '" + fullEventName + "'. Event names must be unique in the system.");
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
            if (bParameter <= 0) {
                throw new InvalidXMLException("Event '" + fullEventName + "': LMSR 'b' must be greater than 0, but it's " + bParameter + ".");
            }
            tradingMethod = new LMSRTradingMethod(bParameter, optionsMap, commission, commissionType);

        } else if (xmlEvent.getGMMethod().getGMOrderBook() != null) {
            // זהו אירוע Order Book
            int baseValue = xmlEvent.getGMMethod().getGMOrderBook().getD();
            int initialInvestment = xmlEvent.getGMMethod().getGMOrderBook().getInitial();
            String allowMintStr = xmlEvent.getGMMethod().getGMOrderBook().getAllowMint();

            if (baseValue <= 0) {
                throw new InvalidXMLException("Event '" + fullEventName + "': order book 'd' must be greater than 0, but it's " + baseValue + ".");
            }
            if (initialInvestment < 0) {
                throw new InvalidXMLException("Event '" + fullEventName + "': order book 'initial' can't be negative, but it's " + initialInvestment + ".");
            }
            // parseBoolean would silently turn a typo like "yes" into false
            if (!"true".equalsIgnoreCase(allowMintStr) && !"false".equalsIgnoreCase(allowMintStr)) {
                throw new InvalidXMLException("Event '" + fullEventName + "': order book 'allow-mint' must be true or false, but it's '" + allowMintStr + "'.");
            }
            boolean allowMint = Boolean.parseBoolean(allowMintStr);

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