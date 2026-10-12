package dto;

import java.util.List;

public record UserDetailsDTO(
        String name,
        double balance,
        List<PortfolioItemDTO> portfolio,
        List<BalanceHistoryItemDTO> balanceHistory, // newest first
        boolean blocked // balance went negative: can't trade until a deposit brings it back to 0 or more
) {}