package com.rockyrunstream.walmart.impl.store;

import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.OptimisticLockException;
import com.rockyrunstream.walmart.impl.SafeCallable;
import com.rockyrunstream.walmart.impl.model.Reservation;
import com.rockyrunstream.walmart.impl.model.ReservationSeat;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.rockyrunstream.walmart.impl.model.Reservation.State.*;

/**
 * In-memory storage. All operations are thread-safe. Access controlled with ReadWriteLock.
 */
@Service
public class ReservationStore {

    private static final Logger log = LoggerFactory.getLogger(ReservationStore.class);

    @Autowired
    private Validator validator;

    private ReadWriteLock transactionLock;

    private Map<Integer, Reservation> reservations;
    private Map<Integer, Reservation> reservationsBySeatHoldId;
    private PriorityQueue<Reservation> pendingReservations;
    private Set<ReservationSeat> pendingSeats;
    private Set<ReservationSeat> reservedSeats;
    private int transactionId;

    public ReservationStore() {
        this.transactionLock = new ReentrantReadWriteLock();
        drop();
    }

    /**
     * @return 2 counts - number of
     */
    public SeatsCounter countReserved() {
        return readOperation(() -> new SeatsCounter(reservedSeats.size(), pendingSeats.size()));
    }


    public Optional<Reservation> getReservationBySeatHoldId(int seatHoldId) {
        return readOperation(() -> {
            final Reservation reservation = reservationsBySeatHoldId.get(seatHoldId);
            if (reservation == null) {
                return Optional.empty();
            } else {
                return Optional.of(CopyUtils.safeCopy(reservation));
            }
        });
    }

    public Optional<Reservation> getOldestPending() {
        return readOperation(() -> {
            while (!pendingReservations.isEmpty()) {
                final Reservation reservation = pendingReservations.peek();
                if (reservation.getState() == PENDING) {
                    final Reservation copy = CopyUtils.safeCopy(reservation);
                    return Optional.of(copy);
                } else {
                    //Not pending anymore - remove from the queue
                    pendingReservations.poll();
                }
            }
            return Optional.empty();
        });
    }


    public Reservation createReservation(Reservation reservation) {
        return writeOperation(() -> {

            //1. Check constraints
            verifyDataConstraints(reservation);

            //2. Check ID uniques
            if (reservationsBySeatHoldId.containsKey(reservation.getSeatHoldId())) {
                throw new InternalServiceException("Reservation with this SeatHoldId already exists");
            }
            if (reservations.containsKey(reservation.getId())) {
                //Double check that ID is unique
                throw new InternalServiceException("Reservation with this id already exists");
            }

            //3. Verify seats availability
            for (ReservationSeat seat : reservation.getSeats()) {
                if (reservedSeats.contains(seat)) {
                    throw new OptimisticLockException("Seat " + seat.getLabel() + " already reserved");
                }
                if (pendingSeats.contains(seat)) {
                    throw new OptimisticLockException("Seat " + seat.getLabel() + " already on hold");
                }
            }

            //4. Save reservation
            reservation.setTransactionId(transactionId++);
            final Reservation storedReservation = CopyUtils.safeCopy(reservation);
            reservations.put(storedReservation.getId(), storedReservation);
            pendingReservations.add(storedReservation);
            reservationsBySeatHoldId.put(storedReservation.getSeatHoldId(), storedReservation);
            pendingSeats.addAll(storedReservation.getSeats());

            return reservation;
        });
    }

    private void verifyDataConstraints(Reservation reservation) {
        //Check data constrains
        final Set<ConstraintViolation<Reservation>> reservationExceptions = validator.validate(reservation);
        if (CollectionUtils.isNotEmpty(reservationExceptions)) {
            final ConstraintViolationException constraintViolationException = new ConstraintViolationException(reservationExceptions);
            throw new InternalServiceException("Data constraint violation error", constraintViolationException);
        }
    }

    public Reservation updateReservation(Reservation reservation) {
        return writeOperation(() -> {

            //1. Check constraints
            verifyDataConstraints(reservation);

            //2. Get stored reservation
            final Reservation storedReservation = reservations.get(reservation.getId());
            if (storedReservation == null) {
                throw new InternalServiceException("Reservation " + storedReservation.getSeatHoldId() + " not found");
            }

            //3. Verify not expired
            if (storedReservation.getTransactionId() > reservation.getTransactionId()) {
                throw new OptimisticLockException("Reservation " + storedReservation.getSeatHoldId() + " already updated");
            }

            //4. Update seat indexes
            if (reservation.getState() != PENDING) {
                // PENDING -> any other state
                pendingSeats.removeAll(storedReservation.getSeats());
            }
            if (reservation.getState() == COMPLETED) {
                // PENDING -> COMPLETED
                reservedSeats.addAll(storedReservation.getSeats());
            }

            //5. Preserve seats, they are immutable as far as store concern
            final List<ReservationSeat> setsCopy = new ArrayList<>();
            storedReservation.getSeats().forEach(s -> setsCopy.add(CopyUtils.safeCopy(s)));
            reservation.setSeats(setsCopy);

            //6. Save
            reservation.setTransactionId(transactionId++);
            CopyUtils.safeCopy(storedReservation, reservation);

            return reservation;
        });
    }

    public SeatMap getSeatMap() {
        return readOperation(() -> {
            //Copy pending
            final List<ReservationSeat> pending = new ArrayList<>(pendingSeats.size());
            pendingSeats.forEach(s -> pending.add(CopyUtils.safeCopy(s)));

            //Copy reserved
            final List<ReservationSeat> reserved = new ArrayList<>(reservedSeats.size());
            reservedSeats.forEach(s -> reserved.add(CopyUtils.safeCopy(s)));

            return new SeatMap(pending, reserved);
        });
    }

    public void drop() {
        writeOperation(() -> {
            this.reservations = new HashMap<>();
            this.reservationsBySeatHoldId = new HashMap<>();
            this.pendingReservations = new PriorityQueue<>(Comparator.comparingLong(Reservation::getExpiresAt));

            this.pendingSeats = new HashSet<>();
            this.reservedSeats = new HashSet<>();
            this.transactionId = 0;
            return Void.TYPE;
        });
    }

    /**
     * Check for double-booking
     */
    public boolean isConsistent() {
        return readOperation(() -> {
            boolean consistent = true;
            final Set<ReservationSeat> allSeats = new HashSet<>();
            for (Map.Entry<Integer, Reservation> entry : reservations.entrySet()) {
                final Reservation reservation = entry.getValue();

                if (reservation.getState() != COMPLETED && reservation.getState() != PENDING) {
                    //Ignore cancelled reservations
                    continue;
                }

                for (ReservationSeat seat : reservation.getSeats()) {
                    if (!allSeats.add(seat)) {
                        log.error("Double-booked seat {}, reservation ID {} ", seat, reservation.getId());
                        log.error("All reservations {} ", reservation);
                        consistent = false;
                        //printAllReservations(seat);
                    }
                }
            }
            return consistent;
        });
    }

    private <T> T readOperation(SafeCallable<T> callable) {
        return lockOperation(callable, transactionLock.readLock());
    }

    private <T> T writeOperation(SafeCallable<T> callable) {
        return lockOperation(callable, transactionLock.writeLock());
    }

    private <T> T lockOperation(SafeCallable<T> callable, Lock lock) {
        lock.lock();
        try {
            return callable.call();
        } finally {
            lock.unlock();
        }
    }

}
