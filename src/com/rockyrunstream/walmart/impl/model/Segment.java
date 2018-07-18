package com.rockyrunstream.walmart.impl.model;

import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.beans.Transient;

public class Segment {

    @PositiveOrZero
    private int rowIndex;

    @PositiveOrZero
    private int start;

    @Positive
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

    @Transient
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
