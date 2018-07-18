package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.BadRequestException;
import com.rockyrunstream.walmart.DataExpired;
import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.NoSeatsAvailable;
import com.rockyrunstream.walmart.OptimisticLockException;
import com.rockyrunstream.walmart.Seat;
import com.rockyrunstream.walmart.SeatHold;
import com.rockyrunstream.walmart.ServiceNotReadyException;
import com.rockyrunstream.walmart.TicketService;
import com.rockyrunstream.walmart.impl.finder.SeatFinder;
import com.rockyrunstream.walmart.impl.model.Reservation;
import com.rockyrunstream.walmart.impl.model.Row;
import com.rockyrunstream.walmart.impl.model.Segment;
import com.rockyrunstream.walmart.impl.model.Venue;
import com.rockyrunstream.walmart.impl.store.ReservationStore;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.rockyrunstream.walmart.impl.model.Reservation.State.*;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    private static final int MAX_TRY = 10;

    private volatile long holdPeriod;

    private volatile boolean timerOn;

    @Autowired
    private ReservationStore reservationStore;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private SeatFinder seatFinder;

    @Override
    public int numSeatsAvailable() {
        final int numSeatsAvailable = reservationStore.getVenue().getAvailable();
        log.debug("numSeatsAvailable {}", numSeatsAvailable);
        return numSeatsAvailable;
    }

    @Override
    public SeatHold findAndHoldSeats(int numSeats, String customerEmail) {
        if (holdPeriod == 0) {
            throw new ServiceNotReadyException("Not initialized");
        }
        log.debug("findAndHoldSeats numSeats {}, customerEmail {}", numSeats, customerEmail);

        //1. Validate request
        if (numSeats <= 0) {
            throw new BadRequestException("numSeats must be positive");
        }

        //Do not validate email here. The only reliable email validation is to send actual email with confirmation code
        if (StringUtils.isBlank(customerEmail)) {
            throw new BadRequestException("customerEmail required");
        }

        //2. Optimistically try to create a seat hold
        final SeatHold result = optimisticOperation(() -> doFindAndHoldSeats(numSeats, customerEmail));

        log.debug("findAndHoldSeats result {}", result);
        return result;
    }

    private SeatHold doFindAndHoldSeats(int numSeats, String customerEmail) {
        //1. Load venue
        final Venue venue = reservationStore.getVenue();

        //2. Check if seats are available
        if (venue.getAvailable() < numSeats) {
            throw new NoSeatsAvailable(numSeats, venue.getAvailable(), venue.getReserved());
        }

        //3. Obtain ids. It might fail, so do it early
        final int seatHoldId = idGenerator.nextSeatHoldId();
        final int reservationId = idGenerator.nextReservationId();

        //4. Find segments
        final List<Segment> segments = seatFinder.find(venue, numSeats);

        //5. Create reservation
        final Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setState(PENDING);
        reservation.setSegments(segments);
        reservation.setEmail(customerEmail);
        reservation.setSeatHoldId(seatHoldId);
        reservation.setExpiresAt(System.currentTimeMillis() + holdPeriod);

        //6. Update venue
        updateVenueSeats(venue, segments, Row.PENDING, Row.AVAILABLE);

        reservationStore.createReservation(reservation, venue);

        //7. Create seat hold
        final SeatHold seatHold = new SeatHold();
        seatHold.setId(reservation.getSeatHoldId());
        seatHold.setExpireAt(reservation.getExpiresAt());
        seatHold.setSets(new ArrayList<>());
        for (Segment segment : reservation.getSegments()) {
            final int rowIndex = segment.getRowIndex();
            final int segmentEnd = segment.getEnd();
            for (int seatIndex = segment.getStart(); seatIndex < segmentEnd; seatIndex++) {
                final Seat seat = new Seat();
                seat.setRow(rowIndex);
                seat.setSeat(seatIndex);
                seatHold.getSets().add(seat);
            }
        }
        return seatHold;
    }

    @Override
    public String reserveSeats(int seatHoldId, String customerEmail) {
        log.debug("reserveSeats seatHoldId {}, customerEmail {}", seatHoldId, customerEmail);
        //1. Validate request
        if (seatHoldId < 0) {
            throw new BadRequestException("seatHoldId must be positive or zero");
        }
        if (StringUtils.isBlank(customerEmail)) {
            throw new BadRequestException("customerEmail required");
        }

        //2. Optimistically reserve seats
        final char[] confirmationToken = optimisticOperation(() -> doReserveSeats(seatHoldId, customerEmail));

        //3. Convert reservation token into string. Not safe, strings are immutable and stays in the memory, potential token leaks
        final String tokenString = new String(confirmationToken);

        //4. Clean up original token
        Arrays.fill(confirmationToken, '*');

        return tokenString;
    }

    private char[] doReserveSeats(int seatHoldId, String customerEmail) {
        final Pair<Reservation, Venue> pair = reservationStore.getReservationBySeatHoldId(seatHoldId)
                .orElseThrow(() -> new BadRequestException("Seat Hold not found " + seatHoldId));

        final Reservation reservation = pair.getKey();
        if (!StringUtils.equals(customerEmail, reservation.getEmail())) {
            //Someone is trying to guess reservation ID and email? Do not return hints to attackers
            throw new BadRequestException("Seat Hold not found " + seatHoldId);
        }

        //Client error or brut force attack
        if (reservation.getState() != PENDING && reservation.getState() != EXPIRED) {
            throw new BadRequestException("Seat Hold not found " + seatHoldId);
        }

        //2. Validate reservation is not expired
        if (reservation.getState() == EXPIRED) {
            throw new DataExpired("Reservation already expired");
        }
        long now = System.currentTimeMillis();
        if (reservation.getExpiresAt() < now) {
            throw new DataExpired("Reservation already expired");
        }

        //3. Verify reservation segments
        final Venue venue = pair.getValue();
        final VerifyNormalizeResult result = verifyNormalise(reservation, venue);

        //4. Generate confirmation token
        return completeConfirmation(reservation, venue, result);
    }

    private char[] completeConfirmation(Reservation reservation, Venue venue, VerifyNormalizeResult result) {
        if (result.isValid()) {
            final char[] confirmationToken = tokenGenerator.generateToken();
            final String tokenHash = tokenGenerator.hash(confirmationToken);
            reservation.setConfirmationCode(tokenHash);
            updateVenueSeats(venue, result.getSegments(), Row.RESERVED, Row.PENDING);
            reservation.setState(COMPLETED);
            reservationStore.updateReservation(reservation, venue);
            return confirmationToken;
        } else {
            log.error("Reservation {} aborted due to internal error", reservation);
            updateVenueSeats(venue, result.getSegments(), Row.AVAILABLE, Row.PENDING);
            reservation.setState(ABORTED);
            reservationStore.updateReservation(reservation, venue);
            throw new InternalServiceException("Can not complete reservation due to internal error, start over");
        }
    }

    /**
     * Convert segment stored in Seat Reservation into valid segment that can be safely used to update seats in the row
     */
    private VerifyNormalizeResult verifyNormalise(Reservation reservation, Venue venue) {
        final List<Row> rows = venue.getRows();
        final VerifyNormalizeResult result = new VerifyNormalizeResult();
        for (Segment segment : reservation.getSegments()) {
            final int rowIndex = segment.getRowIndex();
            if (rowIndex >= rows.size()) {
                //Ooops. Data corrupted, we must abort current reservation and release all pending seats
                log.error("Invalid row index {}, venue rows size {}, reservationId {}, seatHoldId {}", rowIndex, rows.size(), reservation.getId(), reservation.getSeatHoldId());
                result.setValid(false);
                continue;
            }

            final Row row = rows.get(rowIndex);

            final byte[] seats = row.getSeats();
            final int segmentStart = segment.getStart();
            if (segmentStart >= seats.length) {
                log.error("Invalid segmentStart {}, seats.length {}, reservationId {}, seatHoldId {}", segmentStart, seats.length, reservation.getId(), reservation.getSeatHoldId());
                result.setValid(false);
                continue;
            }
            final Segment verifiedSegment = new Segment();
            verifiedSegment.setRowIndex(rowIndex);
            verifiedSegment.setStart(segmentStart);
            result.getSegments().add(verifiedSegment);

            int segmentEnd = segment.getEnd();
            if (segmentEnd > seats.length) {
                log.error("Invalid segmentEnd {}, seats.length {}, reservationId {}, seatHoldId {}", segmentStart, seats.length, reservation.getId(), reservation.getSeatHoldId());
                result.setValid(false);
                segmentEnd = seats.length;
            }
            verifiedSegment.setLength(segmentEnd - segmentStart);
            for (int i = segmentStart; i < segmentEnd; i++) {
                if (seats[i] != Row.PENDING) {
                    log.error("Seat {}:{} is not pending, abort, reservationId {}, seatHoldId {}", rowIndex, i, reservation.getId(), reservation.getSeatHoldId());
                    result.setValid(false);
                }
            }
        }
        if (CollectionUtils.isEmpty(result.getSegments())) {
            log.warn("No verified segments detected, reservation cancelled {}", reservation.getId());
        }
        return result;
    }

    private void updateVenueSeats(Venue venue, List<Segment> segments, byte value, byte expectedValue) {
        //Update seats
        final List<Row> rows = venue.getRows();
        for (Segment segment : segments) {
            final Row row = rows.get(segment.getRowIndex());
            final byte[] seats = row.getSeats();
            final int segmentStart = segment.getStart();
            final int segmentEnd = segment.getEnd();
            for (int i = segmentStart; i < segmentEnd; i++) {
                if (seats[i] == expectedValue) {
                    seats[i] = value;
                }
            }
        }

        //Update counters
        int available = 0;
        for (Row row : venue.getRows()) {
            for (byte seat : row.getSeats()) {
                if (seat == Row.AVAILABLE) {
                    available++;
                }
            }
        }
        venue.setAvailable(available);
    }

    /**
     * Update expired reservations
     */
    @Scheduled(fixedDelay = 1_000)
    public void cleanup() {
        //Initial delay might also work, but for the demo app it is better to turn the timer explicitly, otherwise
        if (!this.timerOn) {
            log.debug("Timer is off");
            return;
        }
        log.debug("Reservation cleanup timer");
        while (true) {
            final Optional<Pair<Reservation, Venue>> optional = reservationStore.getOldestPending();
            if (optional.isPresent()) {
                final Pair<Reservation, Venue> pair = optional.get();
                final Reservation reservation = pair.getKey();
                if (reservation.getState() != PENDING) {
                    log.error("Invalid state - must be pending, reservation ID {}", reservation.getId());
                    break;
                }
                final long now = System.currentTimeMillis();
                if (now > reservation.getExpiresAt()) {
                    expireReservation(reservation, pair.getValue());
                } else {
                    log.info("Last reservation has not expired yet, pause cleanup process");
                    break;
                }
            } else {
                log.info("No pending reservations in the queue, pause cleanup process");
                break;
            }
        }
    }

    private void expireReservation(Reservation reservation, Venue venue) {
        log.info("Reservation {} expired, updating DB", reservation.getId());

        final VerifyNormalizeResult result = verifyNormalise(reservation, venue);
        updateVenueSeats(venue, result.getSegments(), Row.AVAILABLE, Row.PENDING);

        reservation.setState(EXPIRED);
        try {
            log.debug("Updating reservation {}", reservation);
            reservationStore.updateReservation(reservation, venue);
        } catch (OptimisticLockException e) {
            log.debug("Data expired, do nothing. Next iteration will try the same reservation if it or move to the next one", reservation.getId());
        }
    }

    private class VerifyNormalizeResult {
        private boolean valid = true;
        private List<Segment> segments = new LinkedList<>();

        boolean isValid() {
            return valid;
        }

        void setValid(boolean valid) {
            this.valid = valid;
        }

        List<Segment> getSegments() {
            return segments;
        }
    }

    private <T> T optimisticOperation(SafeCallable<T> callable) {
        for (int i = 0; i < MAX_TRY - 1; i++) {
            try {
                return callable.call();
            } catch (OptimisticLockException e) {
                log.debug("Attempt {} failed", i);
            }
        }
        //Give it last try
        return callable.call();
    }

    public void setHoldPeriod(long holdPeriod) {
        if (holdPeriod <= 0) {
            throw new ServiceNotReadyException("Invalid value, must be positive " + holdPeriod);
        }
        this.holdPeriod = holdPeriod;
    }

    public void startTimer() {
        this.timerOn = true;
    }
}

