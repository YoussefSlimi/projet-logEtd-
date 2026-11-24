package org.logetd.dto;

public record PlaceSummaryDTO(Long id,
                              String label,
                              Long monthlyRentMillimes,
                              Long chargesMillimes,
                              Long estimationsMillimes,
                              Long realMonthlyCostMillimes) {
}
