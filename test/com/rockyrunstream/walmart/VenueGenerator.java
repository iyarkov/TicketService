package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.model.Venue;

import java.util.Arrays;

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
        venue.setRows(data);
        return venue;
    }

}
