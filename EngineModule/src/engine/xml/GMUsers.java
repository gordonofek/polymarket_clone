package engine.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "gmUser" })
@XmlRootElement(name = "GM-users")
public class GMUsers {

    @XmlElement(name = "GM-user", required = true)
    protected List<GMUser> gmUser;

    public List<GMUser> getGMUser() {
        if (gmUser == null) {
            gmUser = new ArrayList<>();
        }
        return this.gmUser;
    }
}