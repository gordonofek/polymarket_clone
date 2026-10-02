package servlets;

import com.google.gson.Gson;
import engine.Engine;
import engine.dto.EventDetailsDTO;
import engine.dto.EventSummaryDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(name = "EventsServlet", urlPatterns = "/events")
public class EventsServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // 1. שליפת המנוע מהזיכרון של השרת
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        // Check if the client is asking for a specific event
        String eventName = request.getParameter("eventName");
        response.setContentType("application/json");

        if(eventName == null)
        {
            // SCENARIO 1: No parameter was sent. The client wants the left-side list.
            List<EventSummaryDTO> eventsSummaries = engine.getAllEvents();
            String json = new Gson().toJson(eventsSummaries);
            response.getWriter().write(json);
        }
        else
        {
            // SCENARIO 2: A specific event name was sent. The client wants the right-side details.
            EventDetailsDTO eventDetails = engine.getEventState(eventName);
            String json = new Gson().toJson(eventDetails);
            response.getWriter().write(json);
        }
    }

}
