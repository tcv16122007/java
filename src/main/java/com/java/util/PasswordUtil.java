package com.java.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private static final int WORK_FACTOR = 12;

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty() || rawPassword.length() > 72) {
            throw new IllegalArgumentException("Mật khẩu không hợp lệ");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean verify(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            try {
                return BCrypt.checkpw(rawPassword, storedPassword);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
        return MessageDigest.isEqual(
            rawPassword.getBytes(StandardCharsets.UTF_8),
            storedPassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static boolean isBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 72 ký tự");
        }
    }
}