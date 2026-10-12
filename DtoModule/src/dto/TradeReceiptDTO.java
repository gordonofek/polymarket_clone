package dto;

public record TradeReceiptDTO(
        boolean isSuccess,
        String message, // הודעת הצלחה או פירוט השגיאה (למשל: "Selling is not supported in LMSR")
        double totalCost, // כמה שולם (או התקבל במקרה של מכירה) נטו
        double commission // גובה העמלה שנגבתה
) {}
