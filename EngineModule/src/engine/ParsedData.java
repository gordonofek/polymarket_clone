package engine;

import internals.Event;
import internals.User;
import java.util.List;

public record ParsedData(List<Event> events, List<User> users) {}
