package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.InternalServiceException;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Service
public class RandomService {

    private static final String RANDOM_ALGORITHM = "SHA1PRNG";

    private static final ThreadLocal<SecureRandom> LOCAL_RANDOM = ThreadLocal.withInitial(() -> {
        try {
            return SecureRandom.getInstance(RANDOM_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServiceException("failed to init random:" + RANDOM_ALGORITHM);
        }
    });

    public int nextInt(int bound) {
        return LOCAL_RANDOM.get().nextInt(bound);
    }

    public void nextBytes(byte[] bytes) {
        LOCAL_RANDOM.get().nextBytes(bytes);
    }
}
