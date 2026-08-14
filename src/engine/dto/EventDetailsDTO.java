package engine.dto;

import java.util.List;

public record EventDetailsDTO(
        int id,
        String name,
        String status,
        double poolValue, // מצב חשבון האירוע הנוכחי
        double totalCommission, // סה"כ עמלה שנגבתה בפועל
        List<OptionDetailsDTO> options, // כל האפשרויות והמחירים
        List<TradeRecordDTO> tradeHistory, // היסטוריית המסחר מהחדשה לישנה
        String winningOption // יהיה null או ריק אם האירוע עדיין פתוח
) {}
