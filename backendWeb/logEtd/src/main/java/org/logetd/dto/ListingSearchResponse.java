package org.logetd.dto;

import java.util.List;

public record ListingSearchResponse(List<ListingSummaryDTO> content,
                                    int page,
                                    int size,
                                    long totalElements,
                                    int totalPages) {
}
