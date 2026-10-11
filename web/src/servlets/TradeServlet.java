package servlets;

import com.google.gson.Gson;
import engine.Engine;
import dto.EventDetailsDTO;
import dto.EventSummaryDTO;
import dto.TradeReceiptDTO;
import dto.ActionType;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "TradeServlet", urlPatterns = "/tradeRequest")
public class TradeServlet extends HttpServlet
{
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        // get the engine instance from the ServletContext
        Engine engine = (Engine) getServletContext().getAttribute("engine");
        response.setContentType("application/json");

        String username = request.getParameter("userName");
        String eventName = request.getParameter("eventName");
        String optionName = request.getParameter("optionName");
        String tradeTypeStr = request.getParameter("tradeType");
        String amountStr = request.getParameter("amount");
        String priceLimitStr = request.getParameter("priceLimit");

        // 1. Check for missing essential string parameters
        if (username == null || eventName == null || optionName == null || tradeTypeStr == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"Missing required parameters.\"}");
            return;
        }

        try {
            // 2. Safely parse the Enum and Numbers
            ActionType type = ActionType.valueOf(tradeTypeStr.toUpperCase());
            int amount = Integer.parseInt(amountStr);
            double priceLimit = Double.parseDouble(priceLimitStr);

            // 3. Execute the trade in the engine
            TradeReceiptDTO receipt = engine.placeOrder(username, eventName, optionName, amount, type, priceLimit);
            if (!receipt.isSuccess())
            {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"success\": false, \"message\": \"Internal logic error with the engine in placeOrder function.\"}");
            }
            else
            {
                // 4. Return success
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(new Gson().toJson(receipt));
            }

        } catch (NumberFormatException e) {
            // Caught if amount or priceLimit are null or not valid numbers
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"Amount and Price Limit must be valid numbers.\"}");
        } catch (IllegalArgumentException e) {
            // Caught if tradeTypeStr doesn't match an ActionType enum value (e.g., not "BUY" or "SELL")
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"Invalid trade type.\"}");
        } catch (Exception e) {
            // Catch any engine-level exceptions (e.g., user doesn't have enough money, event is closed)
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"" + e.getMessage() + "\"}");
        }
    }
}
