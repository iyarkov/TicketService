package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.VenueGenerator;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.junit.Assert;
import org.junit.Test;

public class SeatValueFunctionFactoryTest {

    @Test(expected = RuntimeException.class)
    public void testSetValuesNull() {
        final SeatValueFunctionFactory factory = new SeatValueFunctionFactory();
        final Venue venue = VenueGenerator.generate(5, 5);
        factory.setValues(venue, null);
    }

    @Test(expected = RuntimeException.class)
    public void testSetValuesEmpty() {
        final SeatValueFunctionFactory factory = new SeatValueFunctionFactory();
        final Venue venue = VenueGenerator.generate(5, 5);
        factory.setValues(venue, new double[0][0]);
    }

    @Test(expected = RuntimeException.class)
    public void testSetValuesSmall() {
        final SeatValueFunctionFactory factory = new SeatValueFunctionFactory();
        final Venue venue = VenueGenerator.generate(5, 5);
        factory.setValues(venue, new double[5][4]);
    }

    @Test
    public void testGetValueFunction() {
        final SeatValueFunctionFactory factory = new SeatValueFunctionFactory();
        final Venue venue = VenueGenerator.generate(5, 5);
        factory.setValues(venue, new double[5][5]);
        final SeatValueFunction function = factory.getValueFunction(venue);
        Assert.assertTrue(function instanceof SimpleValueFunction);
    }

}
