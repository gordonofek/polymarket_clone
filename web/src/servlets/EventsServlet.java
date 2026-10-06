package servlets;

import com.google.gson.Gson;
import engine.Engine;
import engine.dto.*;
import internals.ActionType;
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        String eventName = request.getParameter("eventName");
        String action = request.getParameter("action");

        response.setContentType("application/json");

        if (eventName == null) {
            // SCENARIO 1: Return ALL events summary
            List<EventSummaryDTO> eventsSummaries = engine.getAllEvents();
            response.getWriter().write(new Gson().toJson(eventsSummaries));
        }
        else if ("participants".equals(action)) {
            // SCENARIO 2: Return ONLY the participants for the requested event
            List<ParticipantHoldingDTO> participants = engine.getEventParticipants(eventName);
            response.getWriter().write(new Gson().toJson(participants));
        }
        else if ("isMarketMaker".equals(action)) {
            // SCENARIO 3: Return a boolean if the user is the Market Maker
            String username = request.getParameter("username");
            boolean isMM = engine.isUserMarketMaker(username, eventName);
            response.getWriter().write(new Gson().toJson(isMM));
        }
        else {
            // SCENARIO 4: Return the general event details
            EventDetailsDTO eventDetails = engine.getEventState(eventName);
            response.getWriter().write(new Gson().toJson(eventDetails));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        String action = request.getParameter("action");
        response.setContentType("application/json");

        if ("startEvent".equals(action))
        {
            // 1. Extract the parameters sent in the POST body
            String eventName = request.getParameter("eventName");
            String username = request.getParameter("username");

            // 2. Execute the engine command
            TradeReceiptDTO receipt = engine.startEvent(username, eventName);

            // 3. Return the receipt as JSON
            response.getWriter().write(new Gson().toJson(receipt));
        }
        else if ("closeEvent".equals(action)) {
            // 1. Extract parameters for closing
            String eventName = request.getParameter("eventName");
            String winningOption = request.getParameter("winningOption");

            // 2. Execute the engine command
            CloseEventReceiptDTO receipt = engine.closeEvent(eventName, winningOption);

            // 3. Return the receipt as JSON
            response.getWriter().write(new Gson().toJson(receipt));
        }
        else if ("placeOrder".equals(action)) {
            // 1. Extract all 6 parameters for the trade
            String username = request.getParameter("username");
            String eventName = request.getParameter("eventName");
            String optionName = request.getParameter("optionName");
            int amount = Integer.parseInt(request.getParameter("amount"));
            ActionType actionType = ActionType.valueOf(request.getParameter("actionType"));
            double limitPrice = Double.parseDouble(request.getParameter("limitPrice"));

            // 2. Execute the engine command
            TradeReceiptDTO receipt = engine.placeOrder(username, eventName, optionName, amount, actionType, limitPrice);

            // 3. Return the receipt as JSON
            response.getWriter().write(new Gson().toJson(receipt));
        }
    }
}
