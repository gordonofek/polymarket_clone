package engine.files;

import internals.EventManager;

import java.io.*;

public class FileHandler {

    public static EventManager loadState(String filePath) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath)))
        {
            return (EventManager) in.readObject();
        }
        catch (IOException | ClassNotFoundException e)
        {
            throw new StateLoadException("Failed to load the system state: " + e.getMessage());
        }
    }

    // פונקציית השמירה מקבלת גם את המנהל וגם את הנתיב שבו צריך לשמור אותו
    public static void saveState(EventManager manager, String filePath) {
        // מומלץ להוסיף פה סיומת לקובץ אם ה-UI מעביר נתיב בלעדיה, למשל:
        // String fullPath = filePath + ".dat";

        // 1. ה-try-with-resources נפתח עם סוגריים עגולים
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath)))
        {
            out.writeObject(manager);
        }
        catch (IOException e)
        {
            throw new StateSaveException("Failed to save the system state: " + e.getMessage());
        }
    }
}
