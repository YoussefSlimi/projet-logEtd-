package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingPhoto;
import org.logetd.domain.listing.ListingPhotoRepository;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.dto.ListingSearchCriteria;
import org.logetd.dto.ListingSearchResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingSearchServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private ListingPhotoRepository listingPhotoRepository;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private ListingSearchService service;

    @Test
    void searchAppliesPaginationAndReturnsProjection() {
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing(1L)), org.springframework.data.domain.PageRequest.of(0, 20), 1));
        when(placeRepository.findByListingIdInOrderByListingIdAscIdAsc(List.of(1L)))
                .thenReturn(List.of(place(10L, 350000L, 50000L, 15000L)));
        when(listingPhotoRepository.findByListingIdInOrderBySortOrderAsc(List.of(1L)))
                .thenReturn(List.of(photo("vitrine.jpg")));

        ListingSearchResponse response = service.search(criteria(null, null, null, null, 0, 20));

        assertEquals(1, response.content().size());
        assertEquals("Monastir", response.content().get(0).city());
        assertEquals("vitrine.jpg", response.content().get(0).photoPath());
        assertEquals(415000L, response.content().get(0).monthlyCostFromMillimes());
        assertEquals(1, response.totalElements());
        assertEquals(20, response.size());
    }

    @Test
    void sizeAboveFiftyIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.search(criteria(null, null, null, null, 0, 100)));

        assertEquals(400, ex.getStatus().value());
        assertEquals("PAGE_SIZE_TOO_LARGE", ex.getCode());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void negativePageIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.search(criteria(null, null, null, null, -1, 20)));

        assertEquals("INVALID_PAGE", ex.getCode());
    }

    @Test
    void incoherentBudgetIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.search(criteria(600000L, 100000L, null, null, 0, 20)));

        assertEquals("INVALID_BUDGET_RANGE", ex.getCode());
    }

    @Test
    void periodRequiresBothDates() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.search(criteria(null, null, LocalDate.of(2026, 12, 1), null, 0, 20)));

        assertEquals("INVALID_PERIOD", ex.getCode());
    }

    @Test
    void bookedPlacesAreExcludedFromResults() {
        LocalDate start = LocalDate.of(2026, 12, 1);
        LocalDate end = LocalDate.of(2026, 12, 20);
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing(1L))));
        when(placeRepository.findByListingIdInOrderByListingIdAscIdAsc(List.of(1L)))
                .thenReturn(List.of(place(10L, 350000L, 50000L, 15000L),
                        place(11L, 200000L, 20000L, 5000L)));
        when(bookingRepository.findBookedPlaceIds(eq(start), eq(end), anyCollection()))
                .thenReturn(Set.of(11L));

        ListingSearchResponse response = service.search(criteria(null, null, start, end, 0, 20));

        assertEquals(1, response.content().get(0).places().size());
        assertEquals(10L, response.content().get(0).places().get(0).id());
        assertEquals(415000L, response.content().get(0).monthlyCostFromMillimes());
    }

    @Test
    void listingWithEveryPlaceBookedIsSkipped() {
        LocalDate start = LocalDate.of(2026, 12, 1);
        LocalDate end = LocalDate.of(2026, 12, 20);
        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing(1L))));
        when(placeRepository.findByListingIdInOrderByListingIdAscIdAsc(List.of(1L)))
                .thenReturn(List.of(place(10L, 350000L, 50000L, 15000L)));
        when(bookingRepository.findBookedPlaceIds(eq(start), eq(end), anyCollection()))
                .thenReturn(Set.of(10L));

        ListingSearchResponse response = service.search(criteria(null, null, start, end, 0, 20));

        assertTrue(response.content().isEmpty());
    }

    private ListingSearchCriteria criteria(Long min, Long max, LocalDate start, LocalDate end, int page, int size) {
        return new ListingSearchCriteria("Monastir", null, min, max, GenderRule.MIXED,
                null, null, null, start, end, page, size);
    }

    private Listing listing(Long id) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setTitle("Studio campus");
        listing.setCity("Monastir");
        listing.setDistrict("Sidi El Hani");
        listing.setGenderRule(GenderRule.MIXED);
        listing.setStatus(ListingStatus.PENDING);
        return listing;
    }

    private Place place(Long id, Long rent, Long charges, Long estimates) {
        Place place = new Place();
        place.setId(id);
        place.setListing(listing(1L));
        place.setLabel("Chambre " + id);
        place.setMonthlyRentMillimes(rent);
        place.setChargesMillimes(charges);
        place.setEstimationsMillimes(estimates);
        return place;
    }

    private ListingPhoto photo(String path) {
        ListingPhoto photo = new ListingPhoto();
        photo.setListing(listing(1L));
        photo.setPath(path);
        return photo;
    }
}
