package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.VenueService;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.junit.Assert;
import org.junit.Test;

public class VenueServiceTest {

    @Test
    public void testSetValuesNull() {
        final VenueService service = new VenueService();
        final Venue prototype = VenueGenerator.generate(5, 5);
        prototype.setMaxHoldTime(1000L);
        service.setVenue(prototype);

        final Venue venue = service.getVenue();
        Assert.assertEquals(venue.getAvailable(), 25);
        Assert.assertEquals(venue.getCapacity(), 25);
        Assert.assertEquals(venue.getPending(), 0);
        Assert.assertEquals(venue.getReserved(), 0);
        Assert.assertEquals(venue.getMaxHoldTime(), 1000L);
        Assert.assertNotNull(venue.getValues());
    }

    @Test(expected = ServiceNotReadyException.class)
    public void testSetValuesEmpty() {
        final VenueService service = new VenueService();
        final Venue venue = VenueGenerator.generate(5, 5);
        venue.setMaxHoldTime(1000L);
        venue.setValues(new double[0][0]);
        service.setVenue(venue);
    }

    @Test(expected = ServiceNotReadyException.class)
    public void testSetValuesSmall() {
        final VenueService service = new VenueService();
        final Venue venue = VenueGenerator.generate(5, 5);
        venue.setMaxHoldTime(1000L);
        venue.setValues(new double[5][4]);
        service.setVenue(venue);
    }

}
