package org.logetd.service;

import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingPhoto;
import org.logetd.domain.listing.ListingPhotoRepository;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.listing.Place;
import org.logetd.domain.listing.PlaceRepository;
import org.logetd.domain.user.User;
import org.logetd.dto.ListingRequest;
import org.logetd.dto.ListingResponse;
import org.logetd.dto.PhotoResponse;
import org.logetd.dto.PlaceRequest;
import org.logetd.dto.PlaceResponse;
import org.logetd.shared.ApiErrorResponse;
import org.logetd.shared.ApiException;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class ListingService {

    public static final int MAX_LISTINGS_PER_OWNER = 3;
    public static final int MIN_PHOTOS = 3;
    public static final int MAX_PHOTOS = 15;

    private final ListingRepository listingRepository;
    private final ListingPhotoRepository listingPhotoRepository;
    private final PlaceRepository placeRepository;
    private final FileStorageService fileStorageService;

    public ListingService(ListingRepository listingRepository,
                          ListingPhotoRepository listingPhotoRepository,
                          PlaceRepository placeRepository,
                          FileStorageService fileStorageService) {
        this.listingRepository = listingRepository;
        this.listingPhotoRepository = listingPhotoRepository;
        this.placeRepository = placeRepository;
        this.fileStorageService = fileStorageService;
    }

    public ListingResponse create(User owner, ListingRequest request) {
        requireOwner(owner);
        if (listingRepository.countByOwnerId(owner.getId()) >= MAX_LISTINGS_PER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LISTING_LIMIT_REACHED",
                    "Un propriétaire ne peut pas publier plus de 3 annonces.",
                    List.of(new ApiErrorResponse.FieldIssue("owner", "maximum 3 listings")));
        }

        Listing listing = new Listing();
        listing.setOwner(owner);
        applyRequest(listing, request);
        listing.setStatus(ListingStatus.DRAFT);
        return toResponse(listingRepository.save(listing), true);
    }

    @Transactional(readOnly = true)
    public ListingResponse getById(Long id, User viewer) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND",
                        "Annonce introuvable."));
        boolean privileged = viewer != null
                && (Objects.equals(viewer.getId(), listing.getOwner().getId()) || viewer.getRole() == Role.ADMIN);
        return toResponse(listing, privileged);
    }

    public ListingResponse update(User owner, Long id, ListingRequest request) {
        requireOwner(owner);
        Listing listing = ownedListing(owner, id);
        applyRequest(listing, request);
        return toResponse(listingRepository.save(listing), true);
    }

    public ListingResponse addPhotos(User owner, Long id, List<MultipartFile> files, List<Boolean> exteriorFlags) {
        requireOwner(owner);
        Listing listing = ownedListing(owner, id);
        List<ListingPhoto> existing = listingPhotoRepository.findByListingIdOrderBySortOrderAsc(id);
        int existingCount = existing.size();
        List<MultipartFile> uploaded = files == null ? List.of() : files;
        int total = existingCount + uploaded.size();

        if (uploaded.isEmpty() || total < MIN_PHOTOS || total > MAX_PHOTOS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_COUNT_INVALID",
                    "Une annonce doit contenir entre 3 et 15 photos.",
                    List.of(new ApiErrorResponse.FieldIssue("files", "entre 3 et 15 photos au total")));
        }

        boolean hasExterior = existing.stream().anyMatch(ListingPhoto::isExterior);
        if (!hasExterior && (exteriorFlags == null || exteriorFlags.stream().noneMatch(Boolean.TRUE::equals))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_EXTERIOR_REQUIRED",
                    "Au moins une photo de l'extérieur est obligatoire.",
                    List.of(new ApiErrorResponse.FieldIssue("exterior", "au moins une photo extérieure")));
        }

        List<String> storedPaths = new ArrayList<>();
        try {
            for (int i = 0; i < uploaded.size(); i++) {
                MultipartFile file = uploaded.get(i);
                boolean exterior = exteriorFlags != null && i < exteriorFlags.size()
                        && Boolean.TRUE.equals(exteriorFlags.get(i));
                hasExterior |= exterior;

                String path = fileStorageService.store(file);
                storedPaths.add(path);
                ListingPhoto photo = new ListingPhoto();
                photo.setListing(listing);
                photo.setPath(path);
                photo.setExterior(exterior);
                photo.setSortOrder(existingCount + i);
                listingPhotoRepository.save(photo);
            }
        } catch (RuntimeException ex) {
            storedPaths.forEach(fileStorageService::delete);
            throw ex;
        }

        boolean hasPlace = !placeRepository.findByListingIdOrderByIdAsc(id).isEmpty();
        submitForReviewIfReady(listing, total, hasExterior, hasPlace);
        return getById(id, owner);
    }

    public ListingResponse addPlace(User owner, Long id, PlaceRequest request) {
        requireOwner(owner);
        Listing listing = ownedListing(owner, id);

        Place place = new Place();
        place.setListing(listing);
        place.setLabel(request.label());
        place.setMonthlyRentMillimes(request.monthlyRentMillimes());
        place.setChargesMillimes(request.chargesMillimes());
        place.setEstimations(request.estimations());
        place.setEstimationsMillimes(request.estimationsMillimes() == null ? 0L : request.estimationsMillimes());
        placeRepository.save(place);

        List<ListingPhoto> photos = listingPhotoRepository.findByListingIdOrderBySortOrderAsc(id);
        submitForReviewIfReady(listing, photos.size(), photos.stream().anyMatch(ListingPhoto::isExterior), true);
        return getById(id, owner);
    }

    private void submitForReviewIfReady(Listing listing, int photoCount, boolean hasExterior, boolean hasPlace) {
        if (listing.getStatus() == ListingStatus.DRAFT
                && photoCount >= MIN_PHOTOS && hasExterior && hasPlace) {
            listing.setStatus(ListingStatus.PENDING);
            listingRepository.save(listing);
        }
    }

    private void applyRequest(Listing listing, ListingRequest request) {
        listing.setTitle(request.title());
        listing.setDescription(request.description());
        listing.setCity(request.city() == null || request.city().isBlank() ? "Monastir" : request.city());
        listing.setExactAddress(request.exactAddress());
        listing.setGenderRule(request.genderRule());
        listing.setDistrict(request.district());
        listing.setFurnished(Boolean.TRUE.equals(request.furnished()));
        listing.setChargesIncluded(Boolean.TRUE.equals(request.chargesIncluded()));
        listing.setInternetIncluded(Boolean.TRUE.equals(request.internetIncluded()));
    }

    private Listing ownedListing(User owner, Long id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND",
                        "Annonce introuvable."));
        if (!Objects.equals(listing.getOwner().getId(), owner.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LISTING_NOT_OWNED",
                    "Vous n'êtes pas propriétaire de cette annonce.");
        }
        return listing;
    }

    private void requireOwner(User user) {
        if (user == null || user.getRole() != Role.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ROLE_REQUIRED",
                    "Seuls les propriétaires peuvent gérer des annonces.");
        }
    }

    private ListingResponse toResponse(Listing listing, boolean showExactAddress) {
        List<PhotoResponse> photos = listingPhotoRepository
                .findByListingIdOrderBySortOrderAsc(listing.getId()).stream()
                .map(PhotoResponse::from).toList();
        List<PlaceResponse> places = placeRepository.findByListingIdOrderByIdAsc(listing.getId()).stream()
                .map(PlaceResponse::from).toList();

        ListingResponse response = ListingResponse.from(listing, photos, places);
        if (showExactAddress) {
            return response;
        }
        return new ListingResponse(
                response.id(), response.title(), response.description(), response.city(),
                null, response.genderRule(), response.status(), response.ownerId(),
                response.photos(), response.places(), response.district(),
                response.furnished(), response.chargesIncluded(), response.internetIncluded()
        );
    }
}
