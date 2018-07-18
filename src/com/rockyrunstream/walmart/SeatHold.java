package com.rockyrunstream.walmart;

import java.util.List;

public class SeatHold {

    private int id;

    private long expireAt;

    private List<Seat> sets;

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

    public List<Seat> getSets() {
        return sets;
    }

    public void setSets(List<Seat> sets) {
        this.sets = sets;
    }

    @Override
    public String toString() {
        return "SeatHold{" +
                "id=" + id +
                ", expireAt=" + expireAt +
                ", sets=" + sets +
                '}';
    }
}
