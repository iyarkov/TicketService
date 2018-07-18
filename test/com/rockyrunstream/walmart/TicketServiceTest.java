package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.TickerServiceConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TickerServiceConfiguration.class)
public class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TickerServiceConfiguration configuration;

    @Test
    public void smoke() {
        configuration.configure();
        ticketService.numSeatsAvailable();
        final SeatHold seatHold = ticketService.findAndHoldSeats(1, "12");
        ticketService.reserveSeats(seatHold.getId(), "12");
    }

}
