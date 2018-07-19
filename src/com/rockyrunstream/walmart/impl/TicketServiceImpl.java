package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.BadRequestException;
import com.rockyrunstream.walmart.DataExpired;
import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.NoSeatsAvailable;
import com.rockyrunstream.walmart.OptimisticLockException;
import com.rockyrunstream.walmart.Seat;
import com.rockyrunstream.walmart.SeatHold;
import com.rockyrunstream.walmart.TicketService;
import com.rockyrunstream.walmart.impl.finder.SeatFinder;
import com.rockyrunstream.walmart.impl.finder.Segment;
import com.rockyrunstream.walmart.impl.model.Reservation;
import com.rockyrunstream.walmart.impl.model.ReservationSeat;
import com.rockyrunstream.walmart.impl.model.Venue;
import com.rockyrunstream.walmart.impl.store.ReservationStore;
import com.rockyrunstream.walmart.impl.store.SeatMap;
import com.rockyrunstream.walmart.impl.store.SeatsCounter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.rockyrunstream.walmart.impl.model.Reservation.State.*;

@Service
public class TicketServiceImpl implements TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketServiceImpl.class);

    private static final int MAX_TRY = 10;

    @Autowired
    private ReservationStore reservationStore;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private SeatFinder seatFinder;

    @Autowired
    private VenueService venueService;

    @Override
    public int numSeatsAvailable() {
        final Venue venue = venueService.getVenue();
        final SeatsCounter counter = reservationStore.countReserved();
        final int numSeatsAvailable = venue.getCapacity() - counter.getTotal();
        log.debug("numSeatsAvailable {}", numSeatsAvailable);
        return numSeatsAvailable;
    }

    @Override
    public SeatHold findAndHoldSeats(int numSeats, String customerEmail) {
        log.debug("findAndHoldSeats numSeats {}, customerEmail {}", numSeats, customerEmail);

        //1. Validate request
        if (numSeats <= 0) {
            throw new BadRequestException("numSeats must be positive");
        }

        //2. Do not validate email here. The only reliable email validation is to send actual email with confirmation code
        if (StringUtils.isBlank(customerEmail)) {
            throw new BadRequestException("customerEmail required");
        }

        //3. Optimistically try to create a seat hold
        final SeatHold result = optimisticOperation(() -> doFindAndHoldSeats(numSeats, customerEmail));

        log.debug("findAndHoldSeats result {}", result);
        return result;
    }

    private SeatHold doFindAndHoldSeats(int numSeats, String customerEmail) {

        //1. Get data
        final SeatMap seats = reservationStore.getSeatMap();
        final Venue venue = venueService.getVenue();

        //2. Check if seats are available. Note - venue is not initialized yet, so do some simple math
        if (venue.getCapacity() - seats.getReservedSeats().size() - seats.getPendingSeats().size() < numSeats) {
            throw new NoSeatsAvailable(numSeats, venue.getAvailable(), venue.getReserved());
        }

        //3. Obtain ids. It might fail, so do it early
        final int seatHoldId = idGenerator.nextSeatHoldId();
        final int reservationId = idGenerator.nextReservationId();

        //4. Update venue rows
        venue.updateSeat(seats);

        //5. Find segments
        final List<Segment> segments = seatFinder.find(venue, numSeats);

        //6. Create reservation
        final Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setState(PENDING);
        reservation.setEmail(customerEmail);
        reservation.setSeatHoldId(seatHoldId);
        reservation.setExpiresAt(System.currentTimeMillis() + venue.getMaxHoldTime());
        reservation.setSeats(new ArrayList<>());

        final Set<ReservationSeat> set = new HashSet<>();
        for (Segment segment : segments) {
            final int rowIndex = segment.getRowIndex();
            final int segmentEnd = segment.getEnd();
            //Double-check seats
            for (int seatIndex = segment.getStart(); seatIndex < segmentEnd; seatIndex++) {
                final ReservationSeat seat = new ReservationSeat(rowIndex, seatIndex);
                reservation.getSeats().add(seat);
                if (!set.add(seat)) {
                    log.error("Seat finder produced incorrect result {}", segments);
                    log.error("Seat {} produced twice", seat);
                    throw new InternalServiceException("Internal error");
                }
            }
        }

        //7. Persist. Optimistic Lock means that one of the seats was taken, and the whole procedure needs to be run again
        reservationStore.createReservation(reservation);

        //8. Create seat hold
        final SeatHold seatHold = new SeatHold();
        seatHold.setId(reservation.getSeatHoldId());
        seatHold.setExpireAt(reservation.getExpiresAt());
        seatHold.setSeats(new ArrayList<>());
        for (ReservationSeat reservationSeat : reservation.getSeats()) {
            final Seat seat = new Seat();
            seat.setRow(reservationSeat.getRow());
            seat.setSeat(reservationSeat.getSeat());
            seatHold.getSeats().add(seat);
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

        //2. Load reservation
        final Reservation reservation = reservationStore.getReservationBySeatHoldId(seatHoldId)
                .orElseThrow(() -> new BadRequestException("Seat Hold not found " + seatHoldId));
        try {
            //1.Verify
            if (!StringUtils.equals(customerEmail, reservation.getEmail())) {
                //Someone is trying to guess reservation ID and email? Do not return hints to attackers
                throw new BadRequestException("Seat Hold not found " + seatHoldId);
            }

            //2. Client error or brut force attack
            if (reservation.getState() != PENDING && reservation.getState() != EXPIRED) {
                throw new BadRequestException("Seat Hold not found " + seatHoldId);
            }

            //3. Validate reservation is not expired
            if (reservation.getState() == EXPIRED) {
                throw new DataExpired("Reservation already expired");
            }
            long now = System.currentTimeMillis();
            if (reservation.getExpiresAt() < now) {
                throw new DataExpired("Reservation already expired");
            }

            //4. Generate token
            final char[] confirmationToken = tokenGenerator.generateToken();
            final String tokenHash = tokenGenerator.hash(confirmationToken);
            reservation.setConfirmationCode(tokenHash);

            //5. Persists
            reservation.setState(COMPLETED);
            reservationStore.updateReservation(reservation);

            //6. Convert reservation token into string. Not safe, strings are immutable and stays in the memory, potential token leaks
            final String tokenString = new String(confirmationToken);
            //7. Clean up original token
            Arrays.fill(confirmationToken, '*');

            return tokenString;
        } catch (OptimisticLockException e) {
            //8. Ouch! It either cleanup service or parallel request
            final Reservation lastCopy = reservationStore.getReservationBySeatHoldId(seatHoldId)
                    .orElseThrow(() -> new BadRequestException("Seat Hold not found " + seatHoldId));
            if (lastCopy.getState() == EXPIRED) {
                throw new DataExpired("Reservation already expired");
            } else {
                //Client error - sent multiple request simultaneously?
                throw new BadRequestException("Invalid state - " + lastCopy.getState());
            }
        }
    }

    /**
     * Expired reservations cleanup timer
     */
    @Scheduled(fixedDelay = 1_000)
    public void cleanup() {
        log.debug("Reservation cleanup timer");
        while (true) {
            final Optional<Reservation> optional = reservationStore.getOldestPending();
            //1. Check if there is PENDING reservation
            if (!optional.isPresent()) {
                log.info("No pending reservations in the queue, pause cleanup process");
                break;
            }
            final Reservation reservation = optional.get();

            //2. Double-check it state
            if (reservation.getState() != PENDING) {
                log.error("Invalid state - must be pending, reservation ID {}", reservation.getId());
                break;
            }

            //3. Check if it is already expired
            final long now = System.currentTimeMillis();
            if (now < reservation.getExpiresAt()) {
                log.info("Last reservation has not expired yet, pause cleanup process");
                break;
            }

            try {
                //4. Update
                reservation.setState(EXPIRED);
                reservationStore.updateReservation(reservation);
                log.info("Last reservation expired {}", reservation);
            } catch (OptimisticLockException e) {
                log.debug("Optimistic update failed {}", reservation);
            }
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
}

