package servlets;

import engine.Engine;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 1. שליפת המנוע מהזיכרון של השרת
        Engine engine = (Engine) getServletContext().getAttribute("engine");

        // 2. קריאת הפרמטר מהלקוח
        String username = request.getParameter("username");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (username == null || username.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            response.getWriter().write("{\"success\": false, \"message\": \"Username can't be empty\"}");
            return;
        }

        // a new name creates the user; an existing name logs back in as that user
        boolean didAdd = engine.addUser(username);

        response.setStatus(HttpServletResponse.SC_OK); // 200 OK
        if (didAdd) {
            response.getWriter().write("{\"success\": true, \"message\": \"User created and logged in\"}");
        } else {
            response.getWriter().write("{\"success\": true, \"message\": \"Welcome back\"}");
        }
    }
}