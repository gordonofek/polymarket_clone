package servlets;

import engine.Engine;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.InputStream;
import java.io.IOException;

@WebServlet(name = "FileUploadServlet", urlPatterns = "/FileUpload")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 1024 * 1024 * 5, maxRequestSize = 1024 * 1024 * 5 * 5)
public class FileUploadServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // 1. קבלת הקובץ מתוך הבקשה (השם "xmlFile" חייב להיות תואם לשם שיישלח מהלקוח)
            Part filePart = request.getPart("xmlFile");

            // 2. קריאת הקובץ ישירות לזיכרון כ-Stream (אסור לשמור לדיסק!)
            InputStream fileContent = filePart.getInputStream();

            // 3. שליפת המנוע והעברת ה-Stream ישירות לפונקציית הטעינה שלך
            Engine engine = (Engine) getServletContext().getAttribute("engine");

            // תצטרך לוודא שהפונקציה שלך במנוע יודעת לקבל InputStream במקום נתיב לקובץ
            engine.loadEventsFromXml(fileContent);

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\": true, \"message\": \"File loaded successfully\"}");

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"" + e.getMessage() + "\"}");
        }
    }
}