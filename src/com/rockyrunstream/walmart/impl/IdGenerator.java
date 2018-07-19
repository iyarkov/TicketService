package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.InternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates unique IDs.  SeatHoldId must be unique only limited period of time while they are pending. Sequence must
 * not be used because next number can be easily guessed
 */
@Service
public class IdGenerator {

    private static final int MAX_TRY = 32;

    private static final Logger log = LoggerFactory.getLogger(IdGenerator.class);

    private AtomicInteger atomicInteger = new AtomicInteger(1);

    @Autowired
    private RandomService randomService;

    private Set<Integer> usedIds = new TreeSet<>();

    public int nextSeatHoldId() {
        for (int i = 0; i < MAX_TRY; i++) {
            int random = randomService.nextInt(Integer.MAX_VALUE);
            if (!usedIds.contains(random)) {
                usedIds.add(random);
                return random;
            }
        }
        //Seriously??
        log.error("Failed to generate random, gave up after {} try");
        throw new InternalServiceException("Failed to generate next SeatHoldId");
    }

    public int nextReservationId() {
        return atomicInteger.getAndIncrement();
    }
}
