package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.model.Row;
import com.rockyrunstream.walmart.impl.model.Venue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simplifies generation of venues
 */
public class VenueGenerator {

    public static Venue generate(int[] rowSizes) {
        byte[][] data = new byte[rowSizes.length][];
        for (int i = 0; i < rowSizes.length; i++) {
            data[i] = new byte[rowSizes[i]];
        }
        return generate(data);
    }

    public static Venue generate(int rows, int seats) {
        final int[] rowSizes = new int[rows];
        Arrays.fill(rowSizes, seats);
        return generate(rowSizes);
    }

    public static Venue generate(byte[][] data) {
        final Venue venue = new Venue();
        final List<Row> rows = new ArrayList<>();
        int capacity = 0;
        for (byte[] seats : data) {
            final Row row = new Row();
            row.setSeats(seats);
            rows.add(row);
            capacity += seats.length;
        }
        venue.setRows(rows);
        venue.setAvailable(capacity);
        venue.setCapacity(capacity);
        return venue;
    }

}
