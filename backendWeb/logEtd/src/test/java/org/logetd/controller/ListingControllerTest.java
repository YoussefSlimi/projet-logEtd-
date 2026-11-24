package org.logetd.controller;

import org.junit.jupiter.api.Test;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.ListingResponse;
import org.logetd.dto.PhotoResponse;
import org.logetd.dto.PlaceResponse;
import org.logetd.security.JwtService;
import org.logetd.service.ListingService;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ListingService listingService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void ownerCreatesListingReturns201() throws Exception {
        when(listingService.create(any(), any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", bearer(1L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Studio campus","description":"desc","city":"Monastir",
                                 "exactAddress":"Rue X","genderRule":"MIXED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.exactAddress").value("Rue X"));
    }

    @Test
    void tenantCannotCreateListing() throws Exception {
        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", bearer(2L, Role.TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Studio campus\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void getListingReturnsMaskedAddress() throws Exception {
        ListingResponse masked = new ListingResponse(10L, "Studio campus", "desc", "Monastir",
                null, GenderRule.MIXED, ListingStatus.DRAFT, 1L, List.of(), List.of());
        when(listingService.getById(eq(10L), any())).thenReturn(masked);

        mockMvc.perform(get("/api/v1/listings/10")
                        .header("Authorization", bearer(2L, Role.TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exactAddress").doesNotExist())
                .andExpect(jsonPath("$.title").value("Studio campus"));
    }

    @Test
    void ownerUpdatesListingReturns200() throws Exception {
        when(listingService.update(any(), eq(10L), any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/listings/10")
                        .header("Authorization", bearer(1L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Studio rénové\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Studio campus"));
    }

    @Test
    void uploadThreePhotosReturns200() throws Exception {
        ListingResponse withPhotos = new ListingResponse(10L, "Studio campus", "desc", "Monastir",
                "Rue X", GenderRule.MIXED, ListingStatus.DRAFT, 1L,
                List.of(new PhotoResponse(1L, "a.jpg", true, 0),
                        new PhotoResponse(2L, "b.jpg", false, 1),
                        new PhotoResponse(3L, "c.jpg", false, 2)),
                List.of());
        when(listingService.addPhotos(any(), eq(10L), any(), any())).thenReturn(withPhotos);

        mockMvc.perform(multipart("/api/v1/listings/10/photos")
                        .file(photo("a.jpg"))
                        .file(photo("b.jpg"))
                        .file(photo("c.jpg"))
                        .param("exterior", "true", "false", "false")
                        .header("Authorization", bearer(1L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(3))
                .andExpect(jsonPath("$.photos[0].exterior").value(true));
    }

    @Test
    void ownerAddsPlaceReturns201() throws Exception {
        ListingResponse withPlace = new ListingResponse(10L, "Studio campus", "desc", "Monastir",
                "Rue X", GenderRule.MIXED, ListingStatus.DRAFT, 1L, List.of(),
                List.of(new PlaceResponse(5L, "Chambre A", 350000L, 50000L, "Internet inclus")));
        when(listingService.addPlace(any(), eq(10L), any())).thenReturn(withPlace);

        mockMvc.perform(post("/api/v1/listings/10/places")
                        .header("Authorization", bearer(1L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Chambre A","monthlyRentMillimes":350000,
                                 "chargesMillimes":50000,"estimations":"Internet inclus"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.places[0].monthlyRentMillimes").value(350000));
    }

    @Test
    void invalidPayloadReturns400WithStandardError() throws Exception {
        mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", bearer(1L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"sans titre\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("title"));
    }

    private String bearer(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return "Bearer " + jwtService.generateAccessToken(id, role);
    }

    private MockMultipartFile photo(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", new byte[]{1, 2, 3});
    }

    private ListingResponse response() {
        return new ListingResponse(10L, "Studio campus", "desc", "Monastir", "Rue X",
                GenderRule.MIXED, ListingStatus.DRAFT, 1L, List.of(), List.of());
    }
}
