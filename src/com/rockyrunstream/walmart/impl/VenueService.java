package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.ServiceNotReadyException;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Makes active venue globally available
 */
@Service
public class VenueService {

    private volatile Venue prototype;

    public Venue getVenue() {
        if (prototype == null) {
            throw new ServiceNotReadyException("Venue service is not initialized");
        }
        //1. Copy shared
        return prototype.getCopy();
    }

    public void setVenue(Venue venue) {
        //1. Verify venue
        if (venue == null) {
            throw new ServiceNotReadyException("Invalid configuration - venue must not empty");
        }

        //2. Rows
        if (venue.getRows() == null || venue.getRows().length == 0) {
            throw new ServiceNotReadyException("Invalid configuration - venue rows must not be empty");
        }

        //3. Seats
        int counter = 0;
        for (byte[] seats : venue.getRows()) {
            if (seats == null || seats.length == 0) {
                throw new ServiceNotReadyException("Invalid configuration - row must not be empty");
            }
            counter += seats.length;
        }
        venue.setCapacity(counter);

        if (venue.getValues() == null) {
            setDefaultValues(venue);
        }

        //4. Values
        final double[][] values = venue.getValues();
        final byte[][] rows = venue.getRows();
        if (values.length < rows.length) {
            throw new ServiceNotReadyException("Invalid configuration - values.length < rows.length");
        }

        for (int i = 0; i < rows.length; i++) {
            final byte[] seat = rows[i];
            final double[] rowValues = values[i];
            if (rowValues == null) {
                throw new ServiceNotReadyException("Invalid configuration - rowValues == null, row " + i);
            }
            if (rowValues.length < seat.length) {
                throw new ServiceNotReadyException("Invalid configuration - rowValues.length < seat.length, row " + i);
            }
        }

        //5. Check other parameters
        if (venue.getMaxHoldTime() <= 0) {
            throw new ServiceNotReadyException("Invalid configuration - MaxHoldTime must be positive");
        }

        //6. Set
        this.prototype = venue;
    }

    private void setDefaultValues(Venue venue) {
        final byte[][] rows = venue.getRows();
        final double[][] values = new double[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            byte[] row = rows[i];
            values[i] = new double[row.length];
            Arrays.fill(values[i], 1f);
        }
        venue.setValues(values);
    }
}
