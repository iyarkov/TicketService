package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.impl.model.Segment;

import java.util.Comparator;

public class SegmentComparator implements Comparator<Segment> {
    @Override
    public int compare(Segment s1, Segment s2) {
        int result = Integer.compare(s1.getLength(), s2.getLength());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(s1.getRowIndex(), s2.getRowIndex());
        if (result != 0) {
            return result;
        }
        return Integer.compare(s1.getStart(), s2.getStart());
    }
}
