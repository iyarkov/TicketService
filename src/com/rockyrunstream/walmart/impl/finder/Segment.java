package com.rockyrunstream.walmart.impl.finder;

/**
 * Uninterrupted row of seats of a same state
 */
public class Segment {

    private int rowIndex;

    private int start;

    private int length;

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getEnd() {
        return start + length;
    }

    @Override
    public String toString() {
        return "Segment{" +
                "rowIndex=" + rowIndex +
                ", start=" + start +
                ", length=" + length +
                '}';
    }
}
