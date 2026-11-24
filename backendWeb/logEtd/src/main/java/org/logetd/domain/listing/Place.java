package org.logetd.domain.listing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "monthly_rent_millimes", nullable = false)
    private Long monthlyRentMillimes;

    @Column(name = "charges_millimes", nullable = false)
    private Long chargesMillimes;

    @Column(name = "estimations", length = 2000)
    private String estimations;

    /** Estimations chiffrées (internet, électricité, etc.) en millimes, pour le coût réel. */
    @Column(name = "estimations_millimes")
    private Long estimationsMillimes = 0L;

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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Long getMonthlyRentMillimes() {
        return monthlyRentMillimes;
    }

    public void setMonthlyRentMillimes(Long monthlyRentMillimes) {
        this.monthlyRentMillimes = monthlyRentMillimes;
    }

    public Long getChargesMillimes() {
        return chargesMillimes;
    }

    public void setChargesMillimes(Long chargesMillimes) {
        this.chargesMillimes = chargesMillimes;
    }

    public String getEstimations() {
        return estimations;
    }

    public void setEstimations(String estimations) {
        this.estimations = estimations;
    }

    public Long getEstimationsMillimes() {
        return estimationsMillimes;
    }

    public void setEstimationsMillimes(Long estimationsMillimes) {
        this.estimationsMillimes = estimationsMillimes;
    }
}
