package org.logetd.service;

import org.junit.jupiter.api.Test;
import org.logetd.domain.listing.Listing;
import org.logetd.domain.listing.ListingRepository;
import org.logetd.domain.user.User;
import org.logetd.domain.user.UserRepository;
import org.logetd.dto.ListingSearchCriteria;
import org.logetd.dto.ListingSearchResponse;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;
import org.logetd.shared.Role;
import org.logetd.shared.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie sur la vraie base que la recherche n'expose que les annonces validées. */
@SpringBootTest
@Transactional
class ListingVisibilityTest {

    @Autowired
    private ListingSearchService searchService;
    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void pendingListingsAreNotVisibleInSearch() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User owner = new User();
        owner.setPhone("+216" + suffix.replaceAll("\\D", "0"));
        owner.setEmail("owner-" + suffix + "@example.test");
        owner.setPasswordHash("hash");
        owner.setFullName("Propriétaire test");
        owner.setRole(Role.OWNER);
        owner.setStatus(UserStatus.ACTIVE);
        userRepository.save(owner);

        String district = "quartier-" + suffix;
        Listing published = listing(owner, ListingStatus.PUBLISHED, district);
        Listing pending = listing(owner, ListingStatus.PENDING, district);

        ListingSearchResponse response = searchService.search(new ListingSearchCriteria(
                "Monastir", district, null, null, null,
                null, null, null, null, null, 0, 50));

        assertTrue(response.content().stream().anyMatch(item -> item.id().equals(published.getId())));
        assertTrue(response.content().stream().noneMatch(item -> item.id().equals(pending.getId())));
        assertEquals(ListingStatus.PUBLISHED, published.getStatus());
    }

    private Listing listing(User owner, ListingStatus status, String district) {
        Listing listing = new Listing();
        listing.setOwner(owner);
        listing.setTitle("Annonce visibilité " + status);
        listing.setCity("Monastir");
        listing.setDistrict(district);
        listing.setStatus(status);
        return listingRepository.save(listing);
    }
}
