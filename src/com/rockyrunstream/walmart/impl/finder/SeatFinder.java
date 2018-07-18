package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.impl.model.Segment;
import com.rockyrunstream.walmart.impl.model.Venue;

import java.util.List;

public interface SeatFinder {

    List<Segment> find(Venue venue, int numSeats);
}
