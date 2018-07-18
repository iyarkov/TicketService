package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.ServiceNotReadyException;

public class SimpleValueFunction implements SeatValueFunction {

    private double[][] values;

    public SimpleValueFunction(double[][] values) {
        if (values == null) {
            throw new ServiceNotReadyException("values must not be null");
        }
        this.values = values;
    }

    @Override
    public double value(int row, int seat) {
        return this.values[row][seat];
    }
}
