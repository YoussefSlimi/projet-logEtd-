package org.logetd.dto;

import org.logetd.domain.booking.Booking;
import org.logetd.shared.BookingStatus;

import java.time.LocalDate;

public record BookingResponse(Long id,
                              Long placeId,
                              Long listingId,
                              Long tenantId,
                              BookingStatus status,
                              LocalDate startDate,
                              LocalDate endDate,
                              Long depositMillimes) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getPlace() == null ? null : booking.getPlace().getId(),
                booking.getPlace() == null || booking.getPlace().getListing() == null
                        ? null : booking.getPlace().getListing().getId(),
                booking.getTenant() == null ? null : booking.getTenant().getId(),
                booking.getStatus(),
                booking.getStartDate(),
                booking.getEndDate(),
                booking.getDepositMillimes()
        );
    }
}
