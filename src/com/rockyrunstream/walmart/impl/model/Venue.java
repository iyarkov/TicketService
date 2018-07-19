package com.rockyrunstream.walmart.impl.model;

import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.impl.store.SeatMap;

public class Venue {

    public static final byte AVAILABLE = 0;
    public static final byte PENDING = 1;
    public static final byte RESERVED = 2;

    /*
     *  Venue parameters
     */
    private int capacity;
    private int reserved;
    private int pending;
    private long maxHoldTime;

    /**
     * Value of every row in the venue. Share for all venue instances
     */
    private double[][] values;

    /**
     * Venue map. Every seat is either available, pending or reserved
     */
    private byte[][] rows;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double[][] getValues() {
        return values;
    }

    public void setValues(double[][] values) {
        this.values = values;
    }

    public byte[][] getRows() {
        return rows;
    }

    public void setRows(byte[][] rows) {
        this.rows = rows;
    }

    public int getReserved() {
        return reserved;
    }

    public void setReserved(int reserved) {
        this.reserved = reserved;
    }

    public int getPending() {
        return pending;
    }

    public void setPending(int pending) {
        this.pending = pending;
    }

    public int getAvailable() {
        return capacity - reserved - pending;
    }

    public long getMaxHoldTime() {
        return maxHoldTime;
    }

    public void setMaxHoldTime(long maxHoldTime) {
        this.maxHoldTime = maxHoldTime;
    }

    public Venue getCopy() {
        final Venue venue = new Venue();
        venue.setCapacity(this.getCapacity());
        venue.setValues(this.getValues());
        venue.setMaxHoldTime(this.getMaxHoldTime());

        //1. Clone rows
        final byte[][] rows = this.getRows();
        final byte[][] rowsClone = new byte[this.getRows().length][];
        for (int i = 0; i < rows.length; i++) {
            final byte[] seats = rows[i];
            final byte[] seatsClone = new byte[seats.length];
            System.arraycopy(seats, 0, seatsClone, 0, seats.length);
            rowsClone[i] = seatsClone;
        }
        venue.setRows(rowsClone);
        return venue;
    }

    public void updateSeat(SeatMap seats) {
        seats.getPendingSeats().forEach(s -> {
            if (rows[s.getRow()][s.getSeat()] != AVAILABLE) {
                throw new InternalServiceException("Data corrupted seat double bucked: " + s.getLabel());
            }
            rows[s.getRow()][s.getSeat()] = Venue.PENDING;
        });
        seats.getReservedSeats().forEach(s -> {
            if (rows[s.getRow()][s.getSeat()] != AVAILABLE) {
                throw new InternalServiceException("Data corrupted seat double bucked: " + s.getLabel());
            }
            rows[s.getRow()][s.getSeat()] = Venue.RESERVED;
        });
        setPending(seats.getPendingSeats().size());
        setReserved(seats.getReservedSeats().size());
    }
}
