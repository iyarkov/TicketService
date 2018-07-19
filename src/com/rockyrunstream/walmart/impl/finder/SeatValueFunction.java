package com.rockyrunstream.walmart.impl.finder;

/**
 * Provides a value for every seat at the venue
 */
public interface SeatValueFunction {

    double value(int row, int seat);
}
