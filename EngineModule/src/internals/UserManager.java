package internals;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
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
}
