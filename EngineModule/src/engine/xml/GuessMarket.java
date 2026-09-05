package engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "gmEvents",
        "gmUsers"
})
@XmlRootElement(name = "Guess-Market")
public class GuessMarket {

    @XmlElement(name = "GM-events", required = true)
    protected GMEvents gmEvents;

    @XmlElement(name = "GM-users", required = true)
    protected GMUsers gmUsers;

    public GMEvents getGMEvents() { return gmEvents; }
    public void setGMEvents(GMEvents value) { this.gmEvents = value; }

    public GMUsers getGMUsers() { return gmUsers; }
    public void setGMUsers(GMUsers value) { this.gmUsers = value; }
}