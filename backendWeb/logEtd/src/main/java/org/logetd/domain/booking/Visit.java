package org.logetd.domain.booking;

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
import org.logetd.domain.listing.Listing;
import org.logetd.domain.user.User;
import org.logetd.shared.VisitStatus;

import java.time.Instant;

@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VisitStatus status = VisitStatus.REQUESTED;

    @Column(name = "proof_code", length = 32)
    private String proofCode;

    @Column(name = "proven_at")
    private Instant provenAt;

    /** Position transmise par l'étudiant lors de la preuve de visite (facultative). */
    @Column(name = "proof_latitude")
    private Double proofLatitude;

    @Column(name = "proof_longitude")
    private Double proofLongitude;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = VisitStatus.REQUESTED;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Listing getListing() {
        return listing;
    }

    public void setListing(Listing listing) {
        this.listing = listing;
    }

    public User getTenant() {
        return tenant;
    }

    public void setTenant(User tenant) {
        this.tenant = tenant;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public void setStatus(VisitStatus status) {
        this.status = status;
    }

    public String getProofCode() {
        return proofCode;
    }

    public void setProofCode(String proofCode) {
        this.proofCode = proofCode;
    }

    public Instant getProvenAt() {
        return provenAt;
    }

    public void setProvenAt(Instant provenAt) {
        this.provenAt = provenAt;
    }

    public Double getProofLatitude() {
        return proofLatitude;
    }

    public void setProofLatitude(Double proofLatitude) {
        this.proofLatitude = proofLatitude;
    }

    public Double getProofLongitude() {
        return proofLongitude;
    }

    public void setProofLongitude(Double proofLongitude) {
        this.proofLongitude = proofLongitude;
    }
}
