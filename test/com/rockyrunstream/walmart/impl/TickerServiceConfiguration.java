package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.VenueGenerator;
import com.rockyrunstream.walmart.impl.finder.SeatValueFunctionFactory;
import com.rockyrunstream.walmart.impl.model.Venue;
import com.rockyrunstream.walmart.impl.store.ReservationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration()
@ComponentScan("com.rockyrunstream.walmart.impl")
@EnableAutoConfiguration
@EnableScheduling
public class TickerServiceConfiguration {

    @Autowired
    private SeatValueFunctionFactory seatValueFunctionFactory;

    @Autowired
    private TicketServiceImpl ticketService;

    @Autowired
    private ReservationStore store;

    //Enough for tests
    private static final long HOLD_PERIOD = 5_000;

    public void configure() {
        configure(VenueGenerator.generate(100, 100));
    }

    public void configure(Venue venue) {
        configure(venue, null);
    }

    public void configure(Venue venue, double[][] seatValues) {
        configure(venue, null, HOLD_PERIOD);
    }

    public void configure(Venue venue, double[][] seatValues, long holdPeriod) {
        store.setVenue(venue);
        ticketService.setHoldPeriod(holdPeriod);
        if (seatValues == null) {
            seatValueFunctionFactory.setDefaultValues(venue);
        } else {
            seatValueFunctionFactory.setValues(venue, seatValues);
        }
        ticketService.startTimer();
    }
}
