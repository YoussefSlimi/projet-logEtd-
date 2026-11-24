package org.logetd.controller;

import org.logetd.dto.ListingSearchCriteria;
import org.logetd.dto.ListingSearchResponse;
import org.logetd.service.ListingSearchService;
import org.logetd.shared.GenderRule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingSearchController {

    private final ListingSearchService listingSearchService;

    public ListingSearchController(ListingSearchService listingSearchService) {
        this.listingSearchService = listingSearchService;
    }

    @GetMapping
    public ListingSearchResponse search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) Long budgetMin,
            @RequestParam(required = false) Long budgetMax,
            @RequestParam(required = false) GenderRule genderRule,
            @RequestParam(required = false) Boolean furnished,
            @RequestParam(required = false) Boolean chargesIncluded,
            @RequestParam(required = false) Boolean internetIncluded,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return listingSearchService.search(new ListingSearchCriteria(
                city, district, budgetMin, budgetMax, genderRule, furnished,
                chargesIncluded, internetIncluded, startDate, endDate, page, size));
    }
}
