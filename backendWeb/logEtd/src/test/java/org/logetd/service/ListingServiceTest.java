package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingPhoto;
import org.logetd.domain.listing.ListingPhotoRepository;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.ListingRequest;
import org.logetd.dto.ListingResponse;
import org.logetd.dto.PlaceRequest;
import org.logetd.shared.ApiException;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private ListingPhotoRepository listingPhotoRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ListingService listingService;

    private final User owner = user(1L, Role.OWNER);
    private final ListingRequest request = new ListingRequest(
            "Studio proche campus", "Aucune description", "Monastir", "Rue X, Monastir", GenderRule.MIXED);

    @Test
    void createReturnsDraftListingWithDefaultCity() {
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> {
            Listing saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        ListingResponse response = listingService.create(owner, request);

        assertEquals(10L, response.id());
        assertEquals(ListingStatus.DRAFT, response.status());
        assertEquals("Monastir", response.city());
        assertEquals(1L, response.ownerId());
        assertEquals("Rue X, Monastir", response.exactAddress());
    }

    @Test
    void createRejectsFourthListing() {
        when(listingRepository.countByOwnerId(1L)).thenReturn(3L);

        ApiException ex = assertThrows(ApiException.class, () -> listingService.create(owner, request));

        assertEquals(400, ex.getStatus().value());
        assertEquals("LISTING_LIMIT_REACHED", ex.getCode());
        verify(listingRepository, never()).save(any());
    }

    @Test
    void createRejectsTenant() {
        ApiException ex = assertThrows(ApiException.class,
                () -> listingService.create(user(2L, Role.TENANT), request));

        assertEquals(403, ex.getStatus().value());
        assertEquals("OWNER_ROLE_REQUIRED", ex.getCode());
        verifyNoInteractions(listingRepository);
    }

    @Test
    void addPhotosRejectsFewerThanThreePhotos() {
        Listing listing = listing(10L, owner);
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(listingPhotoRepository.findByListingIdOrderBySortOrderAsc(10L)).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class,
                () -> listingService.addPhotos(owner, 10L, files(2), List.of(true, false)));

        assertEquals("PHOTO_COUNT_INVALID", ex.getCode());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void addPhotosRequiresAtLeastOneExteriorPhoto() {
        Listing listing = listing(10L, owner);
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(listingPhotoRepository.findByListingIdOrderBySortOrderAsc(10L)).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class,
                () -> listingService.addPhotos(owner, 10L, files(3), List.of(false, false, false)));

        assertEquals("PHOTO_EXTERIOR_REQUIRED", ex.getCode());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void addPhotosStoresFilesAndSubmitsForReviewWhenPlaceExists() {
        Listing listing = listing(10L, owner);
        List<ListingPhoto> storedPhotos = new ArrayList<>();
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(listingPhotoRepository.findByListingIdOrderBySortOrderAsc(10L)).thenAnswer(inv -> storedPhotos);
        when(listingPhotoRepository.save(any(ListingPhoto.class))).thenAnswer(invocation -> {
            ListingPhoto photo = invocation.getArgument(0);
            photo.setId((long) storedPhotos.size() + 1L);
            storedPhotos.add(photo);
            return photo;
        });
        when(placeRepository.findByListingIdOrderByIdAsc(10L)).thenReturn(List.of(new Place()));
        when(fileStorageService.store(any())).thenReturn("a.jpg", "b.jpg", "c.jpg");

        ListingResponse response = listingService.addPhotos(owner, 10L, files(3), List.of(true, false, false));

        assertEquals(3, response.photos().size());
        assertTrue(response.photos().get(0).exterior());
        assertEquals(ListingStatus.PENDING, response.status());
        verify(fileStorageService, times(3)).store(any());
    }

    @Test
    void addPlaceCreatesPlaceWithMillimeAmounts() {
        Listing listing = listing(10L, owner);
        List<Place> storedPlaces = new ArrayList<>();
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(listingPhotoRepository.findByListingIdOrderBySortOrderAsc(10L)).thenReturn(List.of());
        when(placeRepository.findByListingIdOrderByIdAsc(10L)).thenAnswer(inv -> storedPlaces);
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place saved = invocation.getArgument(0);
            saved.setId(5L);
            storedPlaces.add(saved);
            return saved;
        });

        ListingResponse response = listingService.addPlace(owner, 10L,
                new PlaceRequest("Chambre A", 350000L, 50000L, "Internet inclus"));

        assertEquals(1, response.places().size());
        assertEquals(350000L, response.places().get(0).monthlyRentMillimes());
        assertEquals(ListingStatus.DRAFT, response.status());
        verify(placeRepository).save(any(Place.class));
    }

    @Test
    void updateRejectsAnotherOwnersListing() {
        Listing listing = listing(10L, user(99L, Role.OWNER));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        ApiException ex = assertThrows(ApiException.class, () -> listingService.update(owner, 10L, request));

        assertEquals("LISTING_NOT_OWNED", ex.getCode());
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void getByIdMasksExactAddressForOtherUsers() {
        Listing listing = listing(10L, owner);
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        ListingResponse visitor = listingService.getById(10L, user(2L, Role.TENANT));
        ListingResponse ownerView = listingService.getById(10L, owner);
        ListingResponse adminView = listingService.getById(10L, user(3L, Role.ADMIN));

        assertNull(visitor.exactAddress());
        assertEquals("Rue X, Monastir", ownerView.exactAddress());
        assertEquals("Rue X, Monastir", adminView.exactAddress());
    }

    private List<MultipartFile> files(int count) {
        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(new MockMultipartFile("files", "photo" + i + ".jpg", "image/jpeg", new byte[]{1, 2, 3}));
        }
        return files;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setFullName("Test");
        return user;
    }

    private Listing listing(Long id, User owner) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setOwner(owner);
        listing.setTitle(request.title());
        listing.setCity("Monastir");
        listing.setExactAddress(request.exactAddress());
        listing.setStatus(ListingStatus.DRAFT);
        return listing;
    }
}
