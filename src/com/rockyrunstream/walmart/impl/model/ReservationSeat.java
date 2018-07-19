package com.rockyrunstream.walmart.impl.model;

import javax.validation.constraints.PositiveOrZero;

/**
 * ReservationSeat is almost identical to Set. I just want clear separation between internal and external data models.
 */
public class ReservationSeat {

    @PositiveOrZero
    private int row;

    @PositiveOrZero
    private int seat;

    private String label;

    public ReservationSeat(@PositiveOrZero int row, @PositiveOrZero int seat) {
        this.row = row;
        this.seat = seat;
        label = row + ":" + seat;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getSeat() {
        return seat;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReservationSeat that = (ReservationSeat) o;

        if (row != that.row) return false;
        return seat == that.seat;
    }

    @Override
    public int hashCode() {
        int result = row;
        result = 31 * result + seat;
        return result;
    }

    @Override
    public String toString() {
        return "ReservationSeat{" +
                "row=" + row +
                ", seat=" + seat +
                '}';
    }

    public String getLabel() {
        return label;
    }
}
