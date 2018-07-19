package com.rockyrunstream.walmart.impl.store;

import com.rockyrunstream.walmart.impl.model.Reservation;
import com.rockyrunstream.walmart.impl.model.ReservationSeat;

import java.util.ArrayList;

public class CopyUtils {

    static Reservation safeCopy(Reservation reservation) {
        final Reservation copy = new Reservation();
        safeCopy(copy, reservation);
        return copy;
    }

    static void safeCopy(Reservation target, Reservation source) {
        target.setId(source.getId());
        target.setTransactionId(source.getTransactionId());
        target.setState(source.getState());
        target.setSeatHoldId(source.getSeatHoldId());
        target.setExpiresAt(source.getExpiresAt());
        target.setEmail(source.getEmail());
        target.setConfirmationCode(source.getConfirmationCode());
        target.setSeats(new ArrayList<>(source.getSeats().size()));
        for (final ReservationSeat seat : source.getSeats()) {
            final ReservationSeat setCopy = safeCopy(seat);
            target.getSeats().add(setCopy);
        }
    }

     static ReservationSeat safeCopy(ReservationSeat seat) {
        return new ReservationSeat(seat.getRow(), seat.getSeat());
    }
}
