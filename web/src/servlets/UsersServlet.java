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
}
