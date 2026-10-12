package dto;

import java.util.List;
import java.util.Map;

public record UserEventOverviewDTO(
        double totalCommissionPaid,
        Map<String, Double> optionInvestments, // OptionName -> Money Spent
        double profitLoss,
        List<TradeRecordDTO> userTradeHistory, // newest first
        String winningOption,
        Map<String, Integer> optionTotalShares // OptionName -> total shares bought in the event (by everyone)
) {}