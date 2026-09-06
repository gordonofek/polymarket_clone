package engine.dto;

import java.util.Map;

public record UserDetailsDTO(
        String name,
        double balance,
        Map<Integer, Map<String, Integer>> portfolio
) {}