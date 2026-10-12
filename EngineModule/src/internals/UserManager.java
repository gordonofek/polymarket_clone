package internals;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Holds all the users. User names are case-insensitive (MoMo and mOmO are the same user),
 * but each user keeps the spelling they first logged in with.
 */
public class UserManager implements Serializable {
    // lower-cased name -> user
    private Map<String, User> userRegistry;

    // lower-cased names of the users that currently have a client logged in
    private Set<String> loggedInUsers = new HashSet<>();

    public UserManager() {
        this.userRegistry = new HashMap<>();
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    public void addUser(User user)
    {
        if (hasUser(user.getName())){
            throw new IllegalArgumentException("User with the name " + user.getName() + " already exists.");
        }
        this.userRegistry.put(key(user.getName()), user);
    }

    // adds a list of events to registry
    public void addUserList(List<User> list)
    {
        for(User user : list){
            addUser(user);
        }
    }

    public User getUser(String name) {
        return name == null ? null : this.userRegistry.get(key(name));
    }

    public boolean hasUser(String name) {
        return name != null && this.userRegistry.containsKey(key(name));
    }

    public Collection<User> getAllUsers() {
        return this.userRegistry.values();
    }

    public void clearUsers() {
        this.userRegistry.clear();
        this.loggedInUsers.clear();
    }

    public boolean addIfNotExists(String username) {
        if (hasUser(username)) {
            return false; // המשתמש כבר קיים - כישלון
        }

        this.userRegistry.put(key(username), new User(username.trim()));
        return true; // הוסף בהצלחה
    }

    public boolean isLoggedIn(String name) {
        return name != null && loggedInUsers.contains(key(name));
    }

    public void setLoggedIn(String name, boolean loggedIn) {
        if (loggedIn) {
            loggedInUsers.add(key(name));
        } else {
            loggedInUsers.remove(key(name));
        }
    }
}