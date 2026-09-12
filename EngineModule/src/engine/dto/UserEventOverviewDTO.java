package engine.dto;

import java.util.List;
import java.util.Map;

public record UserEventOverviewDTO(
        double totalCommissionPaid,
        Map<String, Double> optionInvestments, // OptionName -> Money Spent
        double profitLoss,
        List<TradeRecordDTO> userTradeHistory,
        String winningOption
) {}