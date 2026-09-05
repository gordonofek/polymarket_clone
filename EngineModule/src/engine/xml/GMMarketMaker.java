package engine.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "event" })
@XmlRootElement(name = "GM-market-maker")
public class GMMarketMaker {

    @XmlElement(required = true)
    protected List<Event> event;

    public List<Event> getEvent() {
        if (event == null) {
            event = new ArrayList<>();
        }
        return this.event;
    }
}