package com.rockyrunstream.walmart.impl.store;

import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.OptimisticLockException;
import com.rockyrunstream.walmart.ServiceNotReadyException;
import com.rockyrunstream.walmart.impl.SafeCallable;
import com.rockyrunstream.walmart.impl.model.Reservation;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.rockyrunstream.walmart.impl.model.Reservation.State.PENDING;

@Service
public class ReservationStore {

    private static final Logger log = LoggerFactory.getLogger(ReservationStore.class);

    @Autowired
    private Validator validator;

    private ReadWriteLock transactionLock;

    private Venue storedVenue;

    private Map<Integer, Reservation> reservations;
    private Map<Integer, Reservation> reservationsBySeatHoldId;
    private PriorityQueue<Reservation> pendingReservations;
    private int transactionId;

    public ReservationStore() {
        this.transactionLock = new ReentrantReadWriteLock();
    }

    public void setVenue(Venue venue) {
        writeOperation(() -> {
            final Set<ConstraintViolation<Venue>> venueExceptions = validator.validate(venue);
            if (CollectionUtils.isNotEmpty(venueExceptions)) {
                log.error("Invalid venue configuration", venueExceptions);
                throw new ConstraintViolationException(venueExceptions);
            }
            this.storedVenue = CopyUtils.safeCopy(venue);
            storedVenue.setTransactionId(transactionId++);

            this.reservations = new TreeMap<>();
            this.reservationsBySeatHoldId = new TreeMap<>();
            this.pendingReservations = new PriorityQueue<>(Comparator.comparingLong(Reservation::getExpiresAt));
            this.transactionId = 0;

            return Void.TYPE;
        });
    }

    private Reservation getReservation(int id) {
        return readOperation(() -> {
            final Reservation reservation = reservations.get(id);
            if (reservation == null) {
                throw new InternalServiceException("Reservation " + id + " not found");
            } else {
                return reservation;
            }
        });
    }

    public Optional<Pair<Reservation, Venue>> getReservationBySeatHoldId(int seatHoldId) {
        return readOperation(() -> {
            if (storedVenue == null) {
                throw new ServiceNotReadyException("Not initialized");
            }
            final Reservation reservation = reservationsBySeatHoldId.get(seatHoldId);
            if (reservation == null) {
                return Optional.empty();
            } else {
                final Reservation copy = CopyUtils.safeCopy(reservation);
                return Optional.of(new ImmutablePair<>(copy, getVenue()));
            }
        });
    }

    public Optional<Pair<Reservation, Venue>> getOldestPending() {
        return readOperation(() -> {
            if (storedVenue == null) {
                throw new ServiceNotReadyException("Not initialized");
            }
            while (!pendingReservations.isEmpty()) {
                final Reservation reservation = pendingReservations.peek();
                if (reservation.getState() == PENDING) {
                    final Reservation copy = CopyUtils.safeCopy(reservation);
                    return Optional.of(new ImmutablePair<>(copy, getVenue()));
                } else {
                    //Not pending anymore - remove from the queue
                    pendingReservations.poll();
                }
            }
            return Optional.empty();
        });
    }


    public Reservation createReservation(Reservation reservation, Venue venue) {
        return writeOperation(() -> {
            if (storedVenue == null) {
                throw new ServiceNotReadyException("Not initialized");
            }
            //1. Check is venue object is expired
            if(venue.getTransactionId() < storedVenue.getTransactionId()) {
                throw new OptimisticLockException("Venue already updated");
            }

            //2. Verify
            verifyDataConstraints(reservation, venue);

            if (reservationsBySeatHoldId.containsKey(reservation.getSeatHoldId())) {
                throw new InternalServiceException("Reservation with this SeatHoldId already exists");
            }

            if (reservations.containsKey(reservation.getId())) {
                //Double check that ID is unique
                throw new InternalServiceException("Reservation with this id already exists");
            }

            //4. Save reservation
            final Reservation storedReservation = CopyUtils.safeCopy(reservation);
            reservations.put(storedReservation.getId(), storedReservation);
            pendingReservations.add(storedReservation);
            reservationsBySeatHoldId.put(storedReservation.getSeatHoldId(), storedReservation);

            //5. Update venue
            venue.setTransactionId(transactionId++);
            CopyUtils.safeCopy(storedVenue, venue);

            return reservation;
        });
    }

    private void verifyDataConstraints(Reservation reservation, Venue venue) {
        //Check data constrains
        final Set<ConstraintViolation<Reservation>> reservationExceptions = validator.validate(reservation);
        if (CollectionUtils.isNotEmpty(reservationExceptions)) {
            final ConstraintViolationException constraintViolationException = new ConstraintViolationException(reservationExceptions);
            throw new InternalServiceException("Data constraint violation error", constraintViolationException);
        }
        final Set<ConstraintViolation<Venue>> venueExceptions = validator.validate(venue);
        if (CollectionUtils.isNotEmpty(venueExceptions)) {
            final ConstraintViolationException constraintViolationException = new ConstraintViolationException(venueExceptions);
            throw new InternalServiceException("Data constraint violation error", constraintViolationException);
        }
    }

    public Reservation updateReservation(Reservation reservation, Venue venue) {
        return writeOperation(() -> {
            if (storedVenue == null) {
                throw new ServiceNotReadyException("Not initialized");
            }
            //1. Check is venue object is expired
            if(venue.getTransactionId() < storedVenue.getTransactionId()) {
                throw new OptimisticLockException("Venue already updated");
            }

            //2. Get stored reservation
            final Reservation storedReservation = getReservation(reservation.getId());

            //3. Verify
            verifyDataConstraints(reservation, venue);

            //4. Save
            CopyUtils.safeCopy(storedReservation, reservation);
            venue.setTransactionId(transactionId++);
            CopyUtils.safeCopy(storedVenue, venue);

            return reservation;
        });
    }

    public Venue getVenue() {
        return readOperation(() -> CopyUtils.safeCopy(storedVenue));
    }

    private <T> T readOperation(SafeCallable<T> callable) {
        return lockOperation(callable, transactionLock.readLock());
    }

    private <T> T writeOperation(SafeCallable<T> callable) {
        return lockOperation(callable, transactionLock.writeLock());
    }

    private <T> T lockOperation(SafeCallable<T> callable, Lock lock) {
        //A venue must be set before any operation can be executed
        lock.lock();
        try {
            return callable.call();
        } finally {
            lock.unlock();
        }
    }

}
