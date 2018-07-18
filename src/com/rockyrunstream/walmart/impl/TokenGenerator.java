package com.rockyrunstream.walmart.impl;

import com.rockyrunstream.walmart.InternalServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
/**
 * Tocken hashing code inspired by
 * @source https://github.com/bitmelt/java-password-hash/blob/master/PasswordHash.java
 */
@Service
public class TokenGenerator {

    private static final char[] SYMBOLS;

    private static final int TOKEN_SIZE = 16;

    private static final int SALT_LENGTH = 256;

    private static final int NUM_ITERATIONS = 256;//10_000

    private static final int KEY_LENGTH = 128;//512

    private static final String PASSWORD_ALGORITHM = "PBKDF2WithHmacSHA1"; //HmacSHA512

    static {
        StringBuilder tmp = new StringBuilder();
        for (char ch = '0'; ch <= '9'; ++ch) {
            tmp.append(ch);
        }
        for (char ch = 'a'; ch <= 'z'; ++ch) {
            tmp.append(ch);
        }
        for (char ch = 'A'; ch <= 'Z'; ++ch) {
            tmp.append(ch);
        }

        SYMBOLS = tmp.toString().toCharArray();
    }

    @Autowired
    private RandomService randomService;

    public char[] generateToken() {
        char[] token = new char[TOKEN_SIZE];

        for (int i = 0; i < TOKEN_SIZE; ++i) {
            token[i] = SYMBOLS[randomService.nextInt(SYMBOLS.length)];
        }
        return token;
    }

    public String hash(char[] token)  {
        // Get a random salt.
        byte[] salt = new byte[SALT_LENGTH / 8];
        randomService.nextBytes(salt);

        // Hash the token
        PBEKeySpec spec = new PBEKeySpec(token, salt, NUM_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = null;
        try {
            skf = SecretKeyFactory.getInstance(PASSWORD_ALGORITHM);
            byte[] passwordHash = skf.generateSecret(spec).getEncoded();

            // Create an encoded string with all information
            Base64.Encoder encoder = Base64.getEncoder();
            String encodedSalt = encoder.encodeToString(salt);
            String encodedPasswordHash = encoder.encodeToString(passwordHash);

            return PASSWORD_ALGORITHM + ":" + NUM_ITERATIONS + ":" + encodedSalt + ":" + encodedPasswordHash;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new InternalServiceException("Failed to generate confirmation token", e);
        }

    }
}
