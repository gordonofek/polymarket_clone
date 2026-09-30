package internals;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventManager implements Serializable {
    private Map<String, Event> eventRegistry;

    public EventManager()
    {
        this.eventRegistry = new HashMap<>();
    }

    // adds an event to the registry
    public void addEvent(Event event) {
        if (this.eventRegistry.containsKey(event.getName()))
        {
            throw new IllegalArgumentException("Event with the id"+event.getName()+" already exists in the registry");
        }
        this.eventRegistry.put(event.getName(),event);
    }

    // adds a list of events to registry
    public void addEventList(List<Event> list)
    {
        for(Event event : list){
            addEvent(event);
        }
    }

    // check if the Registry is Empty return true if it is else return false
    public boolean isRegistryEmpty()
    {
        return eventRegistry.isEmpty();
    }

    // gets the event by id
    public Event getEvent(String name) {
        return this.eventRegistry.get(name);
    }

    // checks if the event with the id is in the registry
    public boolean hasEvent(int id) {
        return this.eventRegistry.containsKey(id);
    }

    // returns all the events in the registry
    public Collection<Event> getAllEvents() {
        return this.eventRegistry.values();
    }

    // clears all the events in the registry
    public void clearEvents() {
        this.eventRegistry.clear();
    }
}
