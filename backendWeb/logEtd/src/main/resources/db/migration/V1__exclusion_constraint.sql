CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
ADD CONSTRAINT no_overlap_active_booking
EXCLUDE USING gist (
    place_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
)
WHERE (status IN ('ACCEPTED', 'DEPOSIT_PENDING', 'CONFIRMED', 'IN_PROGRESS'));
