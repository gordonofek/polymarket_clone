package engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "GM-order-book")
public class GMOrderBook {

    @XmlAttribute(name = "initial", required = true)
    protected int initial;

    @XmlAttribute(name = "d", required = true)
    protected int d;

    @XmlAttribute(name = "allow-mint", required = true)
    protected String allowMint;

    public int getInitial() { return initial; }
    public void setInitial(int value) { this.initial = value; }

    public int getD() { return d; }
    public void setD(int value) { this.d = value; }

    public String getAllowMint() { return allowMint; }
    public void setAllowMint(String value) { this.allowMint = value; }


}