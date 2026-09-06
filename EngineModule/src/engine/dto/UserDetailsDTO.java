package engine.dto;

import java.util.List;

public record UserDetailsDTO(
        String name,
        double balance,
        List<PortfolioItemDTO> portfolio
) {}