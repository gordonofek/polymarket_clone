package servlets;

import com.google.gson.Gson;
import engine.Engine;
import dto.UserDetailsDTO;
import dto.UserEventOverviewDTO;
import dto.UserSummaryDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@WebServlet(name = "UsersServlet", urlPatterns = "/users")
public class UsersServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        String username = request.getParameter("userName");
        String eventName = request.getParameter("eventName");

        response.setContentType("application/json");

        if (username == null) {
            // SCENARIO 1: No username. Return List<UserSummaryDTO> for the left table.
            List<UserSummaryDTO> usersSummaries = engine.getAllUsers();
            response.getWriter().write(new Gson().toJson(usersSummaries));

        } else if (eventName == null) {
            // SCENARIO 2: Username exists, but no eventName. Return UserDetailsDTO for the portfolio.
            UserDetailsDTO userDetails = engine.getUserDetails(username);
            response.getWriter().write(new Gson().toJson(userDetails));

        } else {
            // SCENARIO 3: Both exist. Return UserEventOverviewDTO for the bottom panel.
            UserEventOverviewDTO overview = engine.getUserEventOverview(username, eventName);
            response.getWriter().write(new Gson().toJson(overview));
        }
    }

    // action=deposit / action=withdraw, with userName and amount
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        String action = request.getParameter("action");
        String username = request.getParameter("userName");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        double amount;
        try {
            amount = Double.parseDouble(request.getParameter("amount"));
        } catch (NullPointerException | NumberFormatException e) {
            writeError(response, "The amount must be a number.");
            return;
        }

        try {
            double newBalance;

            if ("deposit".equals(action)) {
                newBalance = engine.depositFunds(username, amount);
            } else if ("withdraw".equals(action)) {
                newBalance = engine.withdrawFunds(username, amount);
            } else {
                throw new IllegalArgumentException("Unknown action '" + action + "'.");
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(Map.of("success", true, "balance", newBalance)));

        } catch (IllegalArgumentException e) {
            // unknown user, amount not positive, or more than the balance
            writeError(response, e.getMessage());
        }
    }

    private static void writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write(new Gson().toJson(Map.of("success", false, "message", message)));
    }
}
