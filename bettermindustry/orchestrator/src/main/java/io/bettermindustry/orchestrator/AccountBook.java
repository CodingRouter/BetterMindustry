package io.bettermindustry.orchestrator;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Properties;

final class AccountBook {
    static final int MAX_ACCOUNTS = 1_024;
    static final int PASSWORD_MIN_CODE_POINTS = 10;
    static final int PASSWORD_MAX_CODE_POINTS = 128;
    static final int PBKDF2_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final Path stateFile;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<String, Account> accounts = new LinkedHashMap<>();

    AccountBook(Path stateFile) {
        this.stateFile = stateFile.toAbsolutePath().normalize();
        load();
    }

    synchronized AccountView register(String username, String password, long now) {
        String normalized = normalizeUsername(username);
        validatePassword(password);
        if (accounts.containsKey(normalized)) {
            throw new IllegalArgumentException("username already exists");
        }
        if (accounts.size() >= MAX_ACCOUNTS) {
            throw new IllegalStateException("account limit reached");
        }

        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Role role = accounts.isEmpty() ? Role.ADMIN : Role.PLAYER;
        Account account = new Account(
            normalized,
            role,
            Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                hashPassword(password, salt, PBKDF2_ITERATIONS)
            ),
            PBKDF2_ITERATIONS,
            now
        );
        accounts.put(normalized, account);
        save();
        return account.view();
    }

    synchronized AccountView authenticate(String username, String password) {
        String normalized;
        try {
            normalized = normalizeUsername(username);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Account account = accounts.get(normalized);
        if (account == null || password == null) return null;

        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getUrlDecoder().decode(account.salt());
            expected = Base64.getUrlDecoder().decode(account.passwordHash());
        } catch (IllegalArgumentException e) {
            return null;
        }
        byte[] actual = hashPassword(password, salt, account.iterations());
        if (!MessageDigest.isEqual(expected, actual)) return null;
        return account.view();
    }

    synchronized AccountView find(String username) {
        try {
            Account account = accounts.get(normalizeUsername(username));
            return account == null ? null : account.view();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void load() {
        if (!Files.isRegularFile(stateFile)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load accounts", e);
        }

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("account.") || !key.endsWith(".role")) continue;
            String username = key.substring("account.".length(), key.length() - ".role".length());
            Account account = loadAccount(properties, username);
            if (account != null) accounts.put(account.username(), account);
        }
    }

    private static Account loadAccount(Properties properties, String username) {
        try {
            String normalized = normalizeUsername(username);
            String prefix = "account." + normalized + ".";
            Role role = Role.valueOf(required(properties, prefix + "role"));
            if (role == Role.GUEST) return null;
            String salt = required(properties, prefix + "salt");
            String hash = required(properties, prefix + "passwordHash");
            int iterations = Integer.parseInt(required(properties, prefix + "iterations"));
            long createdAtMillis = Long.parseLong(required(properties, prefix + "createdAtMillis"));
            if (iterations < 100_000 || iterations > 2_000_000) return null;
            Base64.getUrlDecoder().decode(salt);
            Base64.getUrlDecoder().decode(hash);
            return new Account(normalized, role, salt, hash, iterations, createdAtMillis);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void save() {
        Properties properties = new Properties();
        for (Account account : accounts.values()) {
            String prefix = "account." + account.username() + ".";
            properties.setProperty(prefix + "role", account.role().name());
            properties.setProperty(prefix + "salt", account.salt());
            properties.setProperty(prefix + "passwordHash", account.passwordHash());
            properties.setProperty(prefix + "iterations", Integer.toString(account.iterations()));
            properties.setProperty(prefix + "createdAtMillis", Long.toString(account.createdAtMillis()));
        }

        Path temporary = null;
        try {
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "accounts-", ".properties.tmp");
            try (OutputStream output = Files.newOutputStream(temporary);
                 OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                properties.store(writer, "BetterMindustry accounts");
            }
            try {
                Files.move(temporary, stateFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (AccessDeniedException e) {
                try (OutputStream output = Files.newOutputStream(stateFile);
                     OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
                    properties.store(writer, "BetterMindustry accounts");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to persist accounts", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String normalizeUsername(String username) {
        if (username == null) throw new IllegalArgumentException("username is required");
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{3,32}")) {
            throw new IllegalArgumentException("username must contain 3-32 letters, digits, _ or -");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null) throw new IllegalArgumentException("password is required");
        int codePoints = password.codePointCount(0, password.length());
        if (codePoints < PASSWORD_MIN_CODE_POINTS || codePoints > PASSWORD_MAX_CODE_POINTS) {
            throw new IllegalArgumentException("password must contain 10-128 characters");
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }

    private static byte[] hashPassword(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 is unavailable", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    record AccountView(String username, Role role) {
    }

    private record Account(
        String username,
        Role role,
        String salt,
        String passwordHash,
        int iterations,
        long createdAtMillis
    ) {
        AccountView view() {
            return new AccountView(username, role);
        }
    }
}
