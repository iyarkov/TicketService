package com.rockyrunstream.walmart.impl.store;

import com.rockyrunstream.walmart.impl.model.ReservationSeat;

import java.util.Collection;

public class SeatMap {

    private Collection<ReservationSeat> pendingSeats;
    private Collection<ReservationSeat> reservedSeats;

    public SeatMap(Collection<ReservationSeat> pendingSeats, Collection<ReservationSeat> reservedSeats) {
        this.pendingSeats = pendingSeats;
        this.reservedSeats = reservedSeats;
    }

    public Collection<ReservationSeat> getPendingSeats() {
        return pendingSeats;
    }

    public Collection<ReservationSeat> getReservedSeats() {
        return reservedSeats;
    }
}
