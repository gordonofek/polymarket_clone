package servlets;

import engine.Engine;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

// האנוטציה הזו קריטית - היא מורה לטומקט להריץ את המחלקה הזו אוטומטית!
@WebListener
public class EngineContextListener implements ServletContextListener {

    // מתודה 1: רצה פעם אחת בדיוק, ברגע שהשרת עולה (לפני ששום בקשת HTTP מתקבלת)
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // 1. יוצרים מופע יחיד וגלובלי של המנוע שיצרת בתרגיל 2
        Engine engine = new Engine();

        // 2. שומרים אותו בתוך ה-Context (הזיכרון הגלובלי של השרת)
        // נותנים לו את מפתח הזיהוי "engine" כדי שנוכל לשלוף אותו אחר כך
        sce.getServletContext().setAttribute("engine", engine);

        System.out.println("Engine initialized and loaded to Context!");
    }

    // מתודה 2: רצה פעם אחת בדיוק, ברגע שהשרת יורד או נסגר
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // מתודה זו משמשת לניקוי משאבים.
        // בתרגיל הזה אין בה צורך ממשי כי כשהשרת יורד הנתונים פשוט נעלמים,
        // אבל נהוג לנקות את ה-attribute.
        sce.getServletContext().removeAttribute("engine");
        System.out.println("Server shutting down, Engine removed.");
    }
}