package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.domain.booking.BookingRepository;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingPhotoRepository;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.security.JwtService;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ListingSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private ListingRepository listingRepository;
    @MockitoBean
    private PlaceRepository placeRepository;
    @MockitoBean
    private ListingPhotoRepository listingPhotoRepository;
    @MockitoBean
    private BookingRepository bookingRepository;

    @Test
    void searchWithCityAndBudgetReturnsFilteredResultsAndRealCost() throws Exception {
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTitle("Studio campus");
        listing.setCity("Monastir");
        listing.setDistrict("Sidi El Hani");
        listing.setGenderRule(GenderRule.MIXED);
        listing.setStatus(ListingStatus.PENDING);

        Place place = new Place();
        place.setId(10L);
        place.setListing(listing);
        place.setLabel("Chambre A");
        place.setMonthlyRentMillimes(350000L);
        place.setChargesMillimes(50000L);
        place.setEstimationsMillimes(15000L);

        when(listingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(listing)));
        when(placeRepository.findByListingIdInOrderByListingIdAscIdAsc(List.of(1L)))
                .thenReturn(List.of(place));

        mockMvc.perform(get("/api/v1/listings")
                        .param("city", "Monastir")
                        .param("budgetMax", "600000")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].city").value("Monastir"))
                .andExpect(jsonPath("$.content[0].places[0].realMonthlyCostMillimes").value(415000))
                .andExpect(jsonPath("$.content[0].monthlyCostFromMillimes").value(415000))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void sizeAboveFiftyReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/listings")
                        .param("page", "0")
                        .param("size", "100")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_SIZE_TOO_LARGE"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(listingRepository);
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/listings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String bearer() {
        User user = new User();
        user.setId(2L);
        user.setRole(Role.TENANT);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        return "Bearer " + jwtService.generateAccessToken(2L, Role.TENANT);
    }
}
