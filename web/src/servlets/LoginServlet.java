package servlets;

import engine.Engine;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

// /login logs a user in (a name that is already logged in is refused), /logout frees the name again
@WebServlet(name = "LoginServlet", urlPatterns = {"/login", "/logout"})
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 1. שליפת המנוע מהזיכרון של השרת
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        // 2. קריאת הפרמטר מהלקוח
        String username = request.getParameter("username");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if ("/logout".equals(request.getServletPath())) {
            engine.logout(username);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(Map.of("success", true)));
            return;
        }

        if (username == null || username.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(new Gson().toJson(Map.of("success", false, "message", "User name can't be empty.")));
            return;
        }

        try {
            // the reply carries the name as stored, so the client uses the same spelling as the server
            String loggedInAs = engine.login(username);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(new Gson().toJson(Map.of("success", true, "username", loggedInAs)));
        } catch (IllegalArgumentException e) {
            // that name is already logged in
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write(new Gson().toJson(Map.of("success", false, "message", e.getMessage())));
        }
    }
}
