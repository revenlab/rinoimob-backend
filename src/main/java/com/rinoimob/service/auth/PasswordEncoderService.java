package com.rinoimob.service.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordEncoderService {

    // Argon2id: memory=65536 KB (64 MB), iterations=3, parallelism=1, salt=16 bytes, hash=32 bytes
    private static final Argon2PasswordEncoder ENCODER =
            new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

    public String encodePassword(String password) {
        return ENCODER.encode(password);
    }

    public boolean verifyPassword(String password, String hash) {
        return ENCODER.matches(password, hash);
    }
}
