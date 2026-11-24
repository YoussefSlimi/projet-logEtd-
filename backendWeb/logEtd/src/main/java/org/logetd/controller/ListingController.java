package org.logetd.controller;

import jakarta.validation.Valid;
import org.logetd.domain.user.User;
import org.logetd.dto.ListingRequest;
import org.logetd.dto.ListingResponse;
import org.logetd.dto.PlaceRequest;
import org.logetd.service.ListingService;
import org.logetd.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(@Valid @RequestBody ListingRequest request,
                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.create(currentUser(authentication), request));
    }

    @GetMapping("/{id}")
    public ListingResponse getById(@PathVariable Long id, Authentication authentication) {
        return listingService.getById(id, currentUser(authentication));
    }

    @PutMapping("/{id}")
    public ListingResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ListingRequest request,
                                  Authentication authentication) {
        return listingService.update(currentUser(authentication), id, request);
    }

    /**
     * `exterior[i]` indique si le fichier `files[i]` est une photo extérieure.
     * Les listes sont alignées par index ; un drapeau absent vaut false.
     */
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ListingResponse uploadPhotos(@PathVariable Long id,
                                        @RequestPart(name = "files") List<MultipartFile> files,
                                        @RequestParam(name = "exterior", required = false) List<Boolean> exterior,
                                        Authentication authentication) {
        return listingService.addPhotos(currentUser(authentication), id, files, exterior);
    }

    @PostMapping("/{id}/places")
    public ResponseEntity<ListingResponse> addPlace(@PathVariable Long id,
                                                    @Valid @RequestBody PlaceRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.addPlace(currentUser(authentication), id, request));
    }

    private User currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
