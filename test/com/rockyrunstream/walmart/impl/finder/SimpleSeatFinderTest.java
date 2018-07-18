package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.NoSeatsAvailable;
import com.rockyrunstream.walmart.VenueGenerator;
import com.rockyrunstream.walmart.impl.model.Row;
import com.rockyrunstream.walmart.impl.model.Segment;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FinderContextConfiguration.class)
public class SimpleSeatFinderTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleSeatFinderTest.class);

    private byte[][] venueData;

    private Venue venue;

    private double[][] values;

    @Autowired
    private SimpleSeatFinder finder;

    @Autowired
    private SeatValueFunctionFactory seatValueFunctionFactory;

    @Before
    public void setup() {
        venueData = new byte[][] {
                {0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        };
        venue = VenueGenerator.generate(venueData);
        values = new double[][] {
            {1D, 1D, 1D, 1D, 1D},
            {1D, 1D, 1D, 1D, 1D, 1D},
            {1D, 1D, 1D, 1D, 1D, 1D, 1D},
            {1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D},
            {1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D},
            {1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D},
        };
        seatValueFunctionFactory.setValues(venue, values);
    }

    @Test(expected = NoSeatsAvailable.class)
    public void testFindNotAvailable() {
        finder.find(venue, 1000);
    }

    @Test
    public void testOne() {
        final List<Segment> result = finder.find(venue, 1);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        final Segment segment = result.get(0);
        Assert.assertEquals(1, segment.getLength());
        Assert.assertEquals(0, segment.getRowIndex());
        Assert.assertEquals(0, segment.getStart());
        Assert.assertEquals(0, segment.getStart());
    }

    @Test
    public void testFive() {
        final List<Segment> result = finder.find(venue, 5);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        final Segment segment = result.get(0);
        Assert.assertEquals(5, segment.getLength());
        Assert.assertEquals(0, segment.getRowIndex());
        Assert.assertEquals(0, segment.getStart());
    }

    @Test
    public void testSix() {
        final List<Segment> result = finder.find(venue, 6);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        final Segment segment = result.get(0);
        Assert.assertEquals(6, segment.getLength());
        Assert.assertEquals(1, segment.getRowIndex());
        Assert.assertEquals(0, segment.getStart());
    }

    @Test
    public void testTen() {
        final List<Segment> result = finder.find(venue, 10);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());
        final Segment segment = result.get(0);
        Assert.assertEquals(10, segment.getLength());
        Assert.assertEquals(5, segment.getRowIndex());
        Assert.assertEquals(0, segment.getStart());
    }

    @Test
    public void testEleven() {
        final List<Segment> result = finder.find(venue, 11);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());

        final Segment segment1 = result.get(0);
        Assert.assertEquals(6, segment1.getLength());
        Assert.assertEquals(1, segment1.getRowIndex());
        Assert.assertEquals(0, segment1.getStart());

        final Segment segment2 = result.get(1);
        Assert.assertEquals(5, segment2.getLength());
        Assert.assertEquals(0, segment2.getRowIndex());
        Assert.assertEquals(0, segment2.getStart());
    }

    @Test
    public void test12() {
        final List<Segment> result = finder.find(venue, 12);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(2, result.size());

        final Segment segment1 = result.get(0);
        Assert.assertEquals(6, segment1.getLength());
        Assert.assertEquals(1, segment1.getRowIndex());
        Assert.assertEquals(0, segment1.getStart());

        final Segment segment2 = result.get(1);
        Assert.assertEquals(6, segment2.getLength());
        Assert.assertEquals(2, segment2.getRowIndex());
        Assert.assertEquals(0, segment2.getStart());
    }

    @Test
    public void hotSpot() {
        values[3][5] = 2D;
        final List<Segment> result = finder.find(venue, 1);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());

        final Segment segment1 = result.get(0);
        Assert.assertEquals(3, segment1.getRowIndex());
        Assert.assertEquals(5, segment1.getStart());
        Assert.assertEquals(1, segment1.getLength());
    }

    @Test
    public void pendingSeat() {
        venueData[0][2] = Row.PENDING;
        final List<Segment> result = finder.find(venue, 5);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());

        final Segment segment1 = result.get(0);
        Assert.assertEquals(1, segment1.getRowIndex());
        Assert.assertEquals(0, segment1.getStart());
        Assert.assertEquals(5, segment1.getLength());
    }

    @Test
    public void bookAllEmpty() {
        final List<Segment> result = finder.find(venue, 45);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(10, result.size());
    }

    @Test
    public void bookAllPotHoles() {
        venueData = new byte[][] {
                {0, 1, 0, 2, 0},
                {1, 0, 2, 0, 1, 0},
                {0, 2, 0, 1, 0, 1, 0},
                {2, 0, 1, 0, 1, 0, 1, 0},
                {0, 1, 0, 1, 0, 2, 0, 1, 0},
                {1, 0, 1, 0, 2, 0, 1, 0, 1, 0},
        };
        venue = VenueGenerator.generate(venueData);
        seatValueFunctionFactory.setValues(venue, values);

        final List<Segment> result = finder.find(venue, 24);
        log.info("Result {}", result);
        Assert.assertNotNull(result);
        Assert.assertEquals(24, result.size());
    }

   @Test
    public void performance() {
        venue = VenueGenerator.generate(1000, 100);
        values = new double[1000][100];
        seatValueFunctionFactory.setValues(venue, values);

        final long before = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            finder.find(venue, 100);
        }
        final long after = System.currentTimeMillis();

        log.info("Total time {} ms", after - before);
    }

}
