package com.rockyrunstream.walmart.impl.finder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FinderContextConfiguration {

    @Bean
    public SeatValueFunctionFactory seatValueFunctionFactory() {
        return new SeatValueFunctionFactory();
    }

    @Bean
    public SimpleSeatFinder simpleSeatFinder() {
        return new SimpleSeatFinder();
    }
}
