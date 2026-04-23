package com.rinoimob.service.auth;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class PasswordEncoderService {

    private static final int SALT_ROUNDS = 12;

    public String encodePassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(SALT_ROUNDS));
    }

    public boolean verifyPassword(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }
}
