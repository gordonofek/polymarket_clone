package internals;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EventManger {
    private Map<Integer, Event> eventRegistry;

    public EventManger()
    {
        this.eventRegistry = new HashMap<>();
    }

    // adds an event to the registry
    public void addEvent(Event event) {
        if (this.eventRegistry.containsKey(event.getId()))
        {
            throw new IllegalArgumentException("Event with the id"+event.getId()+" already exists in the registry");
        }
        this.eventRegistry.put(event.getId(),event);
    }

    // gets the event by id
    public Event getEvent(int id) {
        return this.eventRegistry.get(id);
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
