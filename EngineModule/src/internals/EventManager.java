package internals;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EventManager implements Serializable {
    private Map<String, Event> eventRegistry;
    private AtomicInteger globalVersion = new AtomicInteger(0);

    public EventManager()
    {
        this.eventRegistry = new HashMap<>();
    }

    // פונקציה שעוברת על כל האירועים ומחזירה רק את אלו שהשתנו
    public List<Event> getEventsSinceVersion(int clientVersion) {
        List<Event> updatedEvents = new ArrayList<>();
        for (Event event : eventRegistry.values()) {
            if (event.getVersion() > clientVersion) {
                updatedEvents.add(event);
            }
        }
        return updatedEvents;
    }

    public int getGlobalVersion() {
        return globalVersion.get();
    }

    public int incrementAndGetGlobalVersion() {
        return this.globalVersion.incrementAndGet();
    }

    // adds an event to the registry
    public void addEvent(Event event) {
        if (this.eventRegistry.containsKey(event.getName()))
        {
            throw new IllegalArgumentException("Event with the id"+event.getName()+" already exists in the registry");
        }
        event.setVersion(this.incrementAndGetGlobalVersion());

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
    public boolean hasEvent(String name) {
        return this.eventRegistry.containsKey(name);
    }

    // returns all the events in the registry
    public Collection<Event> getAllEvents() {
        return this.eventRegistry.values();
    }

    public Set<String> getAllEventNames() {
        return new HashSet<>(this.eventRegistry.keySet());
    }

    // clears all the events in the registry
    public void clearEvents() {
        this.eventRegistry.clear();
    }
}
