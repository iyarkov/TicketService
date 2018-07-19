package com.rockyrunstream.walmart.impl.model;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

public class Reservation {

    public enum State {
        PENDING, EXPIRED, COMPLETED
    }

    @PositiveOrZero
    private int id;

    @PositiveOrZero
    private int transactionId;

    @PositiveOrZero
    private int seatHoldId;

    @PositiveOrZero
    private long expiresAt;

    @NotNull
    private String email;

    @NotNull
    private State state;

    @NotEmpty
    private List<@Valid ReservationSeat> seats;

    private String confirmationCode;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getSeatHoldId() {
        return seatHoldId;
    }

    public void setSeatHoldId(int seatHoldId) {
        this.seatHoldId = seatHoldId;
    }

    public List<ReservationSeat> getSeats() {
        return seats;
    }

    public void setSeats(List<ReservationSeat> seats) {
        this.seats = seats;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "id=" + id +
                ", transactionId=" + transactionId +
                ", seatHoldId=" + seatHoldId +
                ", expiresAt=" + expiresAt +
                ", email='" + email + '\'' +
                ", state=" + state +
                ", seats=" + seats +
                ", confirmationCode='" + confirmationCode + '\'' +
                '}';
    }
}
