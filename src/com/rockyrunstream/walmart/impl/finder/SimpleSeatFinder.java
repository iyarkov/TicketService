package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.NoSeatsAvailable;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class SimpleSeatFinder implements SeatFinder {

    private static final Logger log = LoggerFactory.getLogger(SimpleSeatFinder.class);

    @Autowired
    private SeatValueFunctionFactory seatValueFunctionFactory;

    @Override
    public List<Segment> find(Venue venue, int numSeats) {
        if (numSeats <= 0) {
            throw new InternalServiceException("numSeats must be positive ");
        }
        final Context context = createContext(venue, numSeats);
        return findSeats(numSeats, context);
    }

    private Context createContext(Venue venue, int numSeats) {
        final Context context = new Context();
        findFreeSegments(context, venue);

        //Double-check free space
        if (context.getAvailable() < numSeats) {
            throw new NoSeatsAvailable(numSeats, context.getAvailable(), context.getReserved());
        }
        if (context.getAvailable() != venue.getAvailable()) {
            log.warn("Data inconsistency, context.getAvailable() = {}, venue.getAvailable() = {}", context.getAvailable(), venue.getAvailable());
        }
        if (context.getPending() != venue.getPending()) {
            log.warn("Data inconsistency, context.getPending() = {}, venue.getPending() = {}", context.getPending(), venue.getPending());
        }
        if (context.getReserved() != venue.getReserved()) {
            log.warn("Data inconsistency, context.getReserved() = {}, venue.getReserved() = {}", context.getReserved(), venue.getReserved());
        }

        context.setValueFunction(seatValueFunctionFactory.getValueFunction(venue));
        return context;
    }

    /**
     * Find all segments - uninterrupted sequence of available seats
     */
    private void findFreeSegments(Context context, Venue venue) {
        final List<Segment> freeSegments = new ArrayList<>();
        int availableCounter = 0;
        int reservedCounter = 0;
        int pendingCounter = 0;
        for (int rowIndex = 0; rowIndex < venue.getRows().length; rowIndex++) {
            Segment activeSegment = null;
            byte previousSeat = -1;
            final byte[] seats = venue.getRows()[rowIndex];
            for (int i = 0; i < seats.length; i++) {
                final byte seat = seats[i];
                //Beginning of a new segment
                if (previousSeat != seat) {
                    addNewSegment(freeSegments, activeSegment, i);
                    //We need only available seats
                    if (seat == Venue.AVAILABLE) {
                        activeSegment = new Segment();
                        activeSegment.setRowIndex(rowIndex);
                        activeSegment.setStart(i);
                    } else {
                        activeSegment = null;
                    }
                }
                previousSeat = seat;
                if (seat == Venue.AVAILABLE) {
                    availableCounter++;
                }
                if (seat == Venue.RESERVED) {
                    reservedCounter++;
                }
                if (seat == Venue.PENDING) {
                    pendingCounter++;
                }
            }
            addNewSegment(freeSegments, activeSegment, seats.length);
        }
        context.setFreeSegments(freeSegments);
        context.setAvailable(availableCounter);
        context.setReserved(reservedCounter);
        context.setPending(pendingCounter);
    }

    private List<Segment> findSeats(int numSeats, Context context) {
        final List<Segment> result = new ArrayList<>();
        final LinkedList<Integer> processingQueue = new LinkedList<>();
        processingQueue.add(numSeats);
        while (!processingQueue.isEmpty()) {
            final int segmentNumSeats = processingQueue.removeFirst();
            final Segment bestSeats = findBestSeats(context, segmentNumSeats);

            //Reservation request is too big - must be split
            if (bestSeats == null) {
                int half = segmentNumSeats / 2;
                processingQueue.add(segmentNumSeats - half);
                processingQueue.add(half);
            } else {
                result.add(bestSeats);
            }
        }
        return result;
    }


    private Segment findBestSeats(Context context, int segmentNumSeats) {
        SeekingSegment bestSegment = null;
        final Collection<Segment> freeSegments = context.getFreeSegments();
        final SeatValueFunction valueFunction = context.getValueFunction();

        for (Segment freeSegment : freeSegments) {
            if (freeSegment.getLength() < segmentNumSeats) {
                continue;
            }
            final SeekingSegment seekingSegment = new SeekingSegment(freeSegment, valueFunction, segmentNumSeats);
            while (seekingSegment.canMove()) {
                seekingSegment.moveRight();
            }
            bestSegment = seekingSegment.chooseBest(bestSegment);
        }

        //Update segments
        if (bestSegment == null) {
            return null;
        } else {
            //Update collection of free segments
            freeSegments.remove(bestSegment.getFreeSegment());
            freeSegments.addAll(bestSegment.newFreeSegments());
            return bestSegment.toSegment();
        }
    }


    private void addNewSegment(Collection<Segment> freeSegments, Segment activeSegment, int leftIndex) {
        if (activeSegment != null) {
            activeSegment.setLength(leftIndex - activeSegment.getStart());
            freeSegments.add(activeSegment);
        }
    }

    class Context {

        private Collection<Segment> freeSegments;

        private int available;
        private int reserved;
        private int pending;

        private SeatValueFunction valueFunction;

        public Collection<Segment> getFreeSegments() {
            return freeSegments;
        }

        public void setFreeSegments(Collection<Segment> freeSegments) {
            this.freeSegments = freeSegments;
        }

        public SeatValueFunction getValueFunction() {
            return valueFunction;
        }

        public void setValueFunction(SeatValueFunction valueFunction) {
            this.valueFunction = valueFunction;
        }

        public int getAvailable() {
            return available;
        }

        public void setAvailable(int available) {
            this.available = available;
        }

        public int getReserved() {
            return reserved;
        }

        public void setReserved(int reserved) {
            this.reserved = reserved;
        }

        public int getPending() {
            return pending;
        }

        public void setPending(int pending) {
            this.pending = pending;
        }
    }

    class SeekingSegment {

        private double score;
        private double bestScore;
        private int bestStart;
        private SeatValueFunction valueFunction;
        private Segment freeSegment;
        private Segment reservationSegment;

        SeekingSegment(Segment freeSegment, SeatValueFunction valueFunction, int segmentNumSeats) {
            //Start from the left size of the free segment
            this.reservationSegment = new Segment();
            reservationSegment.setLength(segmentNumSeats);
            reservationSegment.setRowIndex(freeSegment.getRowIndex());
            reservationSegment.setStart(freeSegment.getStart());
            this.valueFunction = valueFunction;
            this.freeSegment = freeSegment;

            //Compute segment score based on current position
            this.score = 0;
            for (int i = reservationSegment.getStart(); i < reservationSegment.getEnd(); i++) {
                this.score += valueFunction.value(reservationSegment.getRowIndex(), i);
            }
            this.bestScore = this.score;
            this.bestStart = reservationSegment.getStart();
        }

        void moveRight() {
            this.score -= valueFunction.value(reservationSegment.getRowIndex(), reservationSegment.getStart());
            this.score += valueFunction.value(reservationSegment.getRowIndex(), reservationSegment.getEnd());
            reservationSegment.setStart(reservationSegment.getStart() + 1);
            if (this.score > this.bestScore) {
                this.bestScore = this.score;
                this.bestStart = reservationSegment.getStart();
            }
        }

        boolean canMove() {
            return this.freeSegment.getEnd() > reservationSegment.getEnd();
        }

        Segment toSegment() {
            final Segment bestSegment = new Segment();
            bestSegment.setStart(bestStart);
            bestSegment.setRowIndex(reservationSegment.getRowIndex());
            bestSegment.setLength(reservationSegment.getLength());
            return bestSegment;
        }

        SeekingSegment chooseBest(SeekingSegment another) {
            if (another == null) {
                return this;
            }
            if (this.bestScore > another.bestScore) {
                return this;
            }
            if (this.bestScore < another.bestScore) {
                return another;
            }

            //If scores are equal - choose one that located inside smallest free segment
            if (this.freeSegment.getLength() < another.freeSegment.getLength()) {
                return this;
            }

            if (this.freeSegment.getLength() > another.freeSegment.getLength()) {
                return another;
            }

            //Choose randomly - that will reduce collusion with other reservations
            if (Math.random() > 0.5) {
                return this;
            }
            return another;
        }

        List<Segment> newFreeSegments() {
            if (freeSegment.getLength() == reservationSegment.getLength()) {
                //Free segment is completely occupied
                return Collections.emptyList();
            }
            final List<Segment> result = new ArrayList<>(2);
            //Left segment
            final int leftLength = bestStart - freeSegment.getStart();
            if (leftLength > 0) {
                final Segment leftSegment = new Segment();
                leftSegment.setRowIndex(freeSegment.getRowIndex());
                leftSegment.setStart(freeSegment.getStart());
                leftSegment.setLength(leftLength);
                result.add(leftSegment);
            }

            //Right segment
            final int bestEnd = bestStart + reservationSegment.getLength();
            final int rightLength = freeSegment.getEnd() - bestEnd;
            if (rightLength > 0) {
                final Segment rightSegment = new Segment();
                rightSegment.setRowIndex(freeSegment.getRowIndex());
                rightSegment.setStart(bestEnd);
                rightSegment.setLength(rightLength);
                result.add(rightSegment);
            }
            return result;
        }

        public Segment getFreeSegment() {
            return freeSegment;
        }
    }
}

