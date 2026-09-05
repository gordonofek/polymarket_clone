package engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
        "gmlmsr",
        "gmOrderBook"
})
@XmlRootElement(name = "GM-method")
public class GMMethod {

    @XmlElement(name = "GM-LMSR")
    protected GMLMSR gmlmsr;

    @XmlElement(name = "GM-order-book")
    protected GMOrderBook gmOrderBook;

    public GMLMSR getGMLMSR() { return gmlmsr; }
    public void setGMLMSR(GMLMSR value) { this.gmlmsr = value; }

    public GMOrderBook getGMOrderBook() { return gmOrderBook; }
    public void setGMOrderBook(GMOrderBook value) { this.gmOrderBook = value; }
}