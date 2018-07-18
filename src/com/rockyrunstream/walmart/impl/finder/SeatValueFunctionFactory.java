package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.ServiceNotReadyException;
import com.rockyrunstream.walmart.impl.model.Row;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SeatValueFunctionFactory {

    private volatile double[][] values;

    public SeatValueFunction getValueFunction(Venue venue) {
        if (values == null) {
            throw new ServiceNotReadyException("Not initialized");
        }
        return new SimpleValueFunction(values);
    }

    public void setValues(Venue venue, double[][] values) {
        if (values == null || values.length == 0) {
            throw new ServiceNotReadyException("Invalid configuration - seat values must not empty");
        }

        final List<Row> rows = venue.getRows();
        if (values.length < rows.size()) {
            throw new ServiceNotReadyException("Invalid configuration - values.length < rows.size()");
        }

        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            final double[] rowValues = values[i];
            if (rowValues == null) {
                throw new ServiceNotReadyException("Invalid configuration - rowValues == null, row " + i);
            }
            if (rowValues.length < row.getSeats().length) {
                throw new ServiceNotReadyException("Invalid configuration - rowValues.length < row.getSeats().length, row " + i);
            }
        }

        this.values = values;
    }

    public void setDefaultValues(Venue venue) {
        final List<Row> rows = venue.getRows();
        final double[][] values = new double[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            values[i] = new double[row.getSeats().length];
            Arrays.fill(values[i], 1f);
        }
        setValues(venue, values);
    }
}
