package com.rockyrunstream.walmart.impl.model;

import javax.validation.constraints.NotEmpty;

public class Row {

    public static final byte AVAILABLE = 0;
    public static final byte PENDING = 1;
    public static final byte RESERVED = 2;

    @NotEmpty
    private byte[] seats;

    public byte[] getSeats() {
        return seats;
    }

    public void setSeats(byte[] seats) {
        this.seats = seats;
    }
}
