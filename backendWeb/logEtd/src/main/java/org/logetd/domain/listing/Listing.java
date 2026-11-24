package org.logetd.domain.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.logetd.domain.user.User;
import org.logetd.shared.GenderRule;
import org.logetd.shared.ListingStatus;

import java.time.Instant;

@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "city", nullable = false, length = 80)
    private String city = "Monastir";

    @Column(name = "district", length = 80)
    private String district;

    @Column(name = "exact_address", length = 500)
    private String exactAddress;

    @Column(name = "furnished", nullable = false)
    private boolean furnished;

    @Column(name = "charges_included", nullable = false)
    private boolean chargesIncluded;

    @Column(name = "internet_included", nullable = false)
    private boolean internetIncluded;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_rule", length = 20)
    private GenderRule genderRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status = ListingStatus.DRAFT;

    @Column(name = "last_confirmed_at")
    private Instant lastConfirmedAt;

    @PrePersist
    void onCreate() {
        if (city == null || city.isBlank()) {
            city = "Monastir";
        }
        if (status == null) {
            status = ListingStatus.DRAFT;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getExactAddress() {
        return exactAddress;
    }

    public void setExactAddress(String exactAddress) {
        this.exactAddress = exactAddress;
    }

    public boolean isFurnished() {
        return furnished;
    }

    public void setFurnished(boolean furnished) {
        this.furnished = furnished;
    }

    public boolean isChargesIncluded() {
        return chargesIncluded;
    }

    public void setChargesIncluded(boolean chargesIncluded) {
        this.chargesIncluded = chargesIncluded;
    }

    public boolean isInternetIncluded() {
        return internetIncluded;
    }

    public void setInternetIncluded(boolean internetIncluded) {
        this.internetIncluded = internetIncluded;
    }

    public GenderRule getGenderRule() {
        return genderRule;
    }

    public void setGenderRule(GenderRule genderRule) {
        this.genderRule = genderRule;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public Instant getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    public void setLastConfirmedAt(Instant lastConfirmedAt) {
        this.lastConfirmedAt = lastConfirmedAt;
    }
}
