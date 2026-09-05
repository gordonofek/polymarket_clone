package engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "initialCash",
        "gmMarketMaker"
})
@XmlRootElement(name = "GM-user")
public class GMUser {

    @XmlElement(name = "initial-cash")
    protected int initialCash;

    @XmlElement(name = "GM-market-maker")
    protected GMMarketMaker gmMarketMaker;

    @XmlAttribute(name = "name", required = true)
    protected String name;

    public int getInitialCash() { return initialCash; }
    public void setInitialCash(int value) { this.initialCash = value; }

    public GMMarketMaker getGMMarketMaker() { return gmMarketMaker; }
    public void setGMMarketMaker(GMMarketMaker value) { this.gmMarketMaker = value; }

    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
}