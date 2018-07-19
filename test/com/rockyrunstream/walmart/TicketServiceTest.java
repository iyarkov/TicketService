package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.VenueService;
import com.rockyrunstream.walmart.impl.model.Venue;
import com.rockyrunstream.walmart.impl.store.ReservationStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TicketServiceTestConfiguration.class)
public class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private ReservationStore store;

    @Before
    public void setup() {
        final Venue venue = VenueGenerator.generate(10, 20);
        venue.setMaxHoldTime(1000);
        venueService.setVenue(venue);
        store.drop();
    }

    @After
    public void after() {
        if (!store.isConsistent()) {
            throw new RuntimeException("Store double booked");
        }
    }

    @Test
    public void smoke() {
        ticketService.numSeatsAvailable();
        final SeatHold seatHold = ticketService.findAndHoldSeats(1, "12");
        final String token = ticketService.reserveSeats(seatHold.getId(), "12");
        Assert.assertNotNull(token);
    }

    @Test(expected = BadRequestException.class)
    public void invalidSeatsId() {
        final SeatHold seatHold = ticketService.findAndHoldSeats(1, "12");
        ticketService.reserveSeats(1235, "12");
    }

    @Test(expected = BadRequestException.class)
    public void invalidEmail() {
        final SeatHold seatHold = ticketService.findAndHoldSeats(1, "12");
        ticketService.reserveSeats(seatHold.getId(), "1332");
    }

    @Test(expected = BadRequestException.class)
    public void userSameSeatHold() {
        final SeatHold seatHold = ticketService.findAndHoldSeats(1, "12");
        ticketService.reserveSeats(seatHold.getId(), "12");
        ticketService.reserveSeats(seatHold.getId(), "12");
    }

    @Test
    public void getAvailable() {
        final int before = ticketService.numSeatsAvailable();
        final int numSeatsReserved = 100;
        final SeatHold seatHold = ticketService.findAndHoldSeats(numSeatsReserved, "12");

        final int after = ticketService.numSeatsAvailable();
        Assert.assertEquals(after, before - numSeatsReserved);

        ticketService.reserveSeats(seatHold.getId(), "12");
        final int afterCompleted = ticketService.numSeatsAvailable();
        Assert.assertEquals(afterCompleted, before - numSeatsReserved);
    }

}
