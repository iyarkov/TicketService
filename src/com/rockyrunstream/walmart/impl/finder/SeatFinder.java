package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.impl.model.Venue;

import java.util.List;

/**
 * Seat finder interface
 */
public interface SeatFinder {

    List<Segment> find(Venue venue, int numSeats);
}
