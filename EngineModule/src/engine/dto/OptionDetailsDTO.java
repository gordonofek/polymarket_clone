package engine.dto;

import java.util.List;

public record OptionDetailsDTO(
        String optionName,
        double currentPrice, // משמש כערך מניה ב-LMSR או כמחיר עסקה אחרון (LAST) ב-Order Book
        int sharesBought,

        // --- נתונים ייעודיים ל-Order Book ---
        double highestBid,   // מחיר קנייה (BID) הגבוה ביותר
        double lowestAsk,    // מחיר מכירה (ASK) הנמוך ביותר
        double midPrice,     // מחיר אמצע (MID)
        double spread,        // מרווח (SPREAD)
        List<PendingRequestDTO> buyQueue,
        List<PendingRequestDTO> sellQueue
) {}