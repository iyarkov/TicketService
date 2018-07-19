package com.rockyrunstream.walmart.impl.finder;

import com.rockyrunstream.walmart.InternalServiceException;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.springframework.stereotype.Service;
/**
 * Factory for value function. Can be different for different venues, for small/big, or for the same venue but for
 * full/empty.
 *
 * Current implementation just returns simple value function
 */
@Service
public class SeatValueFunctionFactory {

    public SeatValueFunction getValueFunction(Venue venue) {
        if (venue == null) {
            throw new InternalServiceException("Value must not be null");
        }
        return new SimpleValueFunction(venue.getValues());
    }
}
