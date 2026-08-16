package engine;

import engine.xml.GMEvent;
import engine.xml.GuessMarket;
import internals.CommissionType;
import internals.Event;
import internals.LMSREvent;
import internals.LMSRTradingMethod;
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

//    public static List<Event> loadEventsFromXML(String filePath) throws JAXBException, InvalidXMLException {
public static List<Event> loadEventsFromXML(String filePath) throws Exception {
        // 1. Unmarshalling: המרת ה-XML לאובייקטים בעזרת JAXB
        File file = new File(filePath);
        if (!file.exists() || !file.getName().endsWith(".xml")) {
            throw new InvalidXMLException("The file does not exist or is not an XML file.");
        }

//        JAXBContext context = JAXBContext.newInstance(GuessMarket.class);
//        Unmarshaller unmarshaller = context.createUnmarshaller();
//        GuessMarket guessMarket = (GuessMarket) unmarshaller.unmarshal(file);

        JAXBContext context = JAXBContext.newInstance(GuessMarket.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        // maxim wrote this

        // Create a custom XML reader that ignores missing namespace definitions
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(false); // This is the magic line that saves the day!

        XMLReader xmlReader = spf.newSAXParser().getXMLReader();
        SAXSource source = new SAXSource(xmlReader, new InputSource(new FileReader(file)));

        // Unmarshal using our forgiving parser instead of the strict default one
        GuessMarket guessMarket = (GuessMarket) unmarshaller.unmarshal(source);

        //

        // 2. וולידציה והמרה לאובייקטי מנוע
        List<Event> engineEvents = new ArrayList<>();
        Set<Integer> existingIds = new HashSet<>();

        // מעבר על רשימת האירועים ש-JAXB יצר
        for (GMEvent xmlEvent : guessMarket.getGMEvents().getGMEvent()) {

            // איחוד רשימת המילים של השם למחרוזת אחת
            String fullEventName = String.join(" ", xmlEvent.getName());

            // --- וולידציה (Application-wise) ---

            // בדיקת ID ייחודי
            if (!existingIds.add(xmlEvent.getId())) {
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
            int bParameter = xmlEvent.getGMMethod().getGMLMSR().getB();
            LMSRTradingMethod lmsrMethod = new LMSRTradingMethod(bParameter, optionsMap, commission, commissionType);

            // we need to create engine class based on the interface and then activate its constructor.
            // ג. יצירת האירוע האמיתי של המנוע
            List<String> optionsList = xmlEvent.getGMOptions().getGMOption();

            Event newEvent = new LMSREvent(
                    xmlEvent.getId(),
                    fullEventName,
                    xmlEvent.getDescription(),
                    commission,
                    commissionType,
                    optionsList,
                    lmsrMethod
            );

            engineEvents.add(newEvent);
        }
        return engineEvents;
    }
}