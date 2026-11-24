package org.logetd.domain.admin;

import org.logetd.shared.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByIdAsc(ReportStatus status);

    /** Nombre de signalants distincts : un même utilisateur ne compte qu'une fois. */
    @Query("select count(distinct r.reporter.id) from Report r where r.listing.id = :listingId")
    long countDistinctReportersByListingId(@Param("listingId") Long listingId);
}
