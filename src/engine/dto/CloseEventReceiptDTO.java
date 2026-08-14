package engine.dto;

public record CloseEventReceiptDTO(
        boolean isSuccess,
        String message,
        String winningOption,
        double totalPayout, // הסכום ששולם לזוכים
        double commissionTaken, // עמלה שנגבתה בסגירה (אם מוגדר on-close)
        double leftoverPool // מה שנשאר בקופה וחוזר לעושה השוק
) {}
