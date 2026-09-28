package internals;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManager implements Serializable {
    private Map<String, User> userRegistry;

    public UserManager() {
        this.userRegistry = new HashMap<>();
    }

    public void addUser(User user)
    {
        if (this.userRegistry.containsKey(user.getName())){
            throw new IllegalArgumentException("User with the name " + user.getName() + " already exists.");
        }
        this.userRegistry.put(user.getName(), user);
    }

    // adds a list of events to registry
    public void addUserList(List<User> list)
    {
        for(User user : list){
            addUser(user);
        }
    }

    public User getUser(String name) {
        return this.userRegistry.get(name);
    }

    public boolean hasUser(String name) {
        return this.userRegistry.containsKey(name);
    }

    public Collection<User> getAllUsers() {
        return this.userRegistry.values();
    }

    public void clearUsers() {
        this.userRegistry.clear();
    }

    public synchronized boolean addIfNotExists(String username) {
        // הבדיקה וההוספה קורות יחד תחת המנעול כדי למנוע מצבי מרוץ
        if (this.userRegistry.containsKey(username)) {
            return false; // המשתמש כבר קיים - כישלון
        }

        // המשתמש לא קיים - נוסיף אותו (בהנחה שקיימת מחלקת User מתאימה)
        this.userRegistry.put(username, new User(username));
        return true; // הוסף בהצלחה
    }
}
