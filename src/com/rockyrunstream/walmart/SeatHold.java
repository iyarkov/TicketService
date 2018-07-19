package com.rockyrunstream.walmart;

import java.util.List;

public class SeatHold {

    private int id;

    private long expireAt;

    private List<Seat> seats;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void setSeats(List<Seat> seats) {
        this.seats = seats;
    }

    @Override
    public String toString() {
        return "SeatHold{" +
                "id=" + id +
                ", expireAt=" + expireAt +
                ", seats=" + seats +
                '}';
    }
}
