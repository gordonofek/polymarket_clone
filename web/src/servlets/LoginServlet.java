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
        String username = request.getParameter("userName");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        boolean didAdd = engine.addUser(username);

        if(didAdd)
        {
            // הוספת המשתמש למנוע
            response.setStatus(HttpServletResponse.SC_OK); // 200 OK
            response.getWriter().write("{\"success\": true, \"message\": \"Logged in successfully\"}");
        }
        else
        {
            // החזרת שגיאה - המשתמש כבר קיים
            response.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
            response.getWriter().write("{\"success\": false, \"message\": \"Username already exists\"}");
        }
    }
}