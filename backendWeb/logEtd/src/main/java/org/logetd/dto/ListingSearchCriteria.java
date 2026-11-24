package org.logetd.dto;

import org.logetd.shared.GenderRule;

import java.time.LocalDate;

public record ListingSearchCriteria(String city,
                                    String district,
                                    Long budgetMin,
                                    Long budgetMax,
                                    GenderRule genderRule,
                                    Boolean furnished,
                                    Boolean chargesIncluded,
                                    Boolean internetIncluded,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    int page,
                                    int size) {
}
