package com.rockyrunstream.walmart.impl.store;

import com.rockyrunstream.walmart.impl.model.*;

import java.util.ArrayList;

public class CopyUtils {

    static Reservation safeCopy(Reservation reservation) {
        final Reservation copy = new Reservation();
        safeCopy(copy, reservation);
        return copy;
    }


    static void safeCopy(Reservation target, Reservation source) {
        target.setId(source.getId());
        target.setSeatHoldId(source.getSeatHoldId());
        target.setExpiresAt(source.getExpiresAt());
        target.setEmail(source.getEmail());
        target.setState(source.getState());
        target.setSegments(new ArrayList<>(source.getSegments().size()));
        for (final Segment segment : source.getSegments()) {
            final Segment segmentCopy = new Segment();
            segmentCopy.setLength(segment.getLength());
            segmentCopy.setRowIndex(segment.getRowIndex());
            segmentCopy.setStart(segment.getStart());
            target.getSegments().add(segmentCopy);
        }
    }

     static Venue safeCopy(Venue venue) {
        final Venue copy = new Venue();
        safeCopy(copy, venue);
        return copy;
    }

    static void safeCopy(Venue target, Venue source) {
        target.setAvailable(source.getAvailable());
        target.setCapacity(source.getCapacity());
        target.setTransactionId(source.getTransactionId());
        target.setRows(new ArrayList<>(source.getRows().size()));
        for (final Row row : source.getRows()) {
            final Row rowCopy = new Row();

            final byte[] sourceSeats = row.getSeats();
            final byte[] targetSeats = new byte[sourceSeats.length];
            System.arraycopy(sourceSeats, 0, targetSeats, 0, sourceSeats.length);
            rowCopy.setSeats(targetSeats);
            target.getRows().add(rowCopy);
        }
    }
}
