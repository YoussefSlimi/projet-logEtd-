package org.logetd.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.shared.BookingStatus;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingPhoto;
import org.logetd.domain.listing.ListingPhotoRepository;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.dto.ListingSearchCriteria;
import org.logetd.dto.ListingSearchResponse;
import org.logetd.dto.ListingSummaryDTO;
import org.logetd.dto.PlaceSummaryDTO;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ListingSearchService {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;
    private static final List<BookingStatus> ACTIVE_BOOKING_STATUSES = List.of(
            BookingStatus.ACCEPTED, BookingStatus.DEPOSIT_PENDING,
            BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    private final ListingRepository listingRepository;
    private final PlaceRepository placeRepository;
    private final ListingPhotoRepository listingPhotoRepository;
    private final BookingRepository bookingRepository;

    public ListingSearchService(ListingRepository listingRepository,
                                PlaceRepository placeRepository,
                                ListingPhotoRepository listingPhotoRepository,
                                BookingRepository bookingRepository) {
        this.listingRepository = listingRepository;
        this.placeRepository = placeRepository;
        this.listingPhotoRepository = listingPhotoRepository;
        this.bookingRepository = bookingRepository;
    }

    public ListingSearchResponse search(ListingSearchCriteria criteria) {
        validate(criteria);
        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), Sort.by("id").ascending());
        Page<Listing> page = listingRepository.findAll(buildSpecification(criteria), pageable);

        List<Listing> listings = page.getContent();
        if (listings.isEmpty()) {
            return new ListingSearchResponse(List.of(), criteria.page(), criteria.size(),
                    page.getTotalElements(), page.getTotalPages());
        }
        List<Long> listingIds = listings.stream().map(Listing::getId).toList();

        Map<Long, List<Place>> placesByListing = placeRepository
                .findByListingIdInOrderByListingIdAscIdAsc(listingIds).stream()
                .collect(Collectors.groupingBy(place -> place.getListing().getId()));

        Set<Long> bookedPlaceIds = criteria.startDate() != null && criteria.endDate() != null
                ? bookingRepository.findBookedPlaceIds(criteria.startDate(), criteria.endDate(),
                        ACTIVE_BOOKING_STATUSES)
                : Set.of();

        Map<Long, String> firstPhotoByListing = new LinkedHashMap<>();
        listingPhotoRepository.findByListingIdInOrderBySortOrderAsc(listingIds).forEach(photo ->
                firstPhotoByListing.putIfAbsent(photo.getListing().getId(), photo.getPath()));

        boolean periodRequested = criteria.startDate() != null;
        List<ListingSummaryDTO> content = new ArrayList<>();
        for (Listing listing : listings) {
            List<PlaceSummaryDTO> availablePlaces = placesByListing
                    .getOrDefault(listing.getId(), List.of()).stream()
                    .filter(place -> !bookedPlaceIds.contains(place.getId()))
                    .map(this::summarize)
                    .toList();
            if (periodRequested && availablePlaces.isEmpty()) {
                continue;
            }
            Long cheapest = availablePlaces.stream()
                    .map(PlaceSummaryDTO::realMonthlyCostMillimes)
                    .min(Long::compareTo)
                    .orElse(null);
            content.add(new ListingSummaryDTO(
                    listing.getId(), listing.getTitle(), listing.getCity(), listing.getDistrict(),
                    listing.getGenderRule(), listing.getStatus(),
                    firstPhotoByListing.get(listing.getId()), cheapest, availablePlaces));
        }

        return new ListingSearchResponse(content, criteria.page(), criteria.size(),
                page.getTotalElements(), page.getTotalPages());
    }

    private PlaceSummaryDTO summarize(Place place) {
        long rent = nz(place.getMonthlyRentMillimes());
        long charges = nz(place.getChargesMillimes());
        long estimates = place.getEstimationsMillimes() == null ? 0L : place.getEstimationsMillimes();
        return new PlaceSummaryDTO(place.getId(), place.getLabel(), rent, charges, estimates,
                rent + charges + estimates);
    }

    private Specification<Listing> buildSpecification(ListingSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Depuis l'Étape 7, seules les annonces validées par l'admin sont visibles.
            predicates.add(cb.equal(root.get("status"), ListingStatus.PUBLISHED));

            if (hasText(criteria.city())) {
                predicates.add(cb.equal(cb.lower(root.get("city")), criteria.city().toLowerCase(Locale.ROOT)));
            }
            if (hasText(criteria.district())) {
                predicates.add(cb.like(cb.lower(root.get("district")),
                        "%" + criteria.district().toLowerCase(Locale.ROOT) + "%"));
            }
            if (criteria.genderRule() != null) {
                predicates.add(cb.equal(root.get("genderRule"), criteria.genderRule()));
            }
            if (criteria.furnished() != null) {
                predicates.add(cb.equal(root.get("furnished"), criteria.furnished()));
            }
            if (criteria.chargesIncluded() != null) {
                predicates.add(cb.equal(root.get("chargesIncluded"), criteria.chargesIncluded()));
            }
            if (criteria.internetIncluded() != null) {
                predicates.add(cb.equal(root.get("internetIncluded"), criteria.internetIncluded()));
            }
            if (criteria.budgetMin() != null || criteria.budgetMax() != null) {
                Join<Listing, Place> places = root.join("places");
                if (criteria.budgetMin() != null) {
                    predicates.add(cb.ge(places.get("monthlyRentMillimes"), criteria.budgetMin()));
                }
                if (criteria.budgetMax() != null) {
                    predicates.add(cb.le(places.get("monthlyRentMillimes"), criteria.budgetMax()));
                }
                query.distinct(true);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void validate(ListingSearchCriteria criteria) {
        if (criteria.page() < 0) {
            throw badRequest("INVALID_PAGE", "La page doit être positive ou nulle.", "page", ">= 0");
        }
        if (criteria.size() > MAX_PAGE_SIZE) {
            throw badRequest("PAGE_SIZE_TOO_LARGE", "La taille de page ne peut pas dépasser 50.",
                    "size", "<= 50");
        }
        if (criteria.size() < 1) {
            throw badRequest("INVALID_PAGE_SIZE", "La taille de page doit être au moins 1.", "size", ">= 1");
        }
        if (criteria.budgetMin() != null && criteria.budgetMax() != null
                && criteria.budgetMin() > criteria.budgetMax()) {
            throw badRequest("INVALID_BUDGET_RANGE", "budgetMin ne peut pas dépasser budgetMax.",
                    "budgetMin", "<= budgetMax");
        }
        boolean hasStart = criteria.startDate() != null;
        boolean hasEnd = criteria.endDate() != null;
        if (hasStart != hasEnd) {
            throw badRequest("INVALID_PERIOD", "startDate et endDate doivent être fournis ensemble.",
                    "startDate", "endDate requis");
        }
        if (hasStart && criteria.endDate().isBefore(criteria.startDate())) {
            throw badRequest("INVALID_PERIOD", "La date de fin doit suivre la date de début.",
                    "endDate", ">= startDate");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }

    private ApiException badRequest(String code, String message, String field, String issue) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message,
                List.of(new ApiErrorResponse.FieldIssue(field, issue)));
    }
}
