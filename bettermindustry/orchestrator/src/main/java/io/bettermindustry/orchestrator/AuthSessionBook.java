package io.bettermindustry.orchestrator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

final class AuthSessionBook {
    static final long SESSION_LIFETIME_MILLIS = 7L * 24L * 60L * 60L * 1_000L;
    static final int MAX_SESSIONS = 64;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> tokenOwners = new LinkedHashMap<>();

    Issued issue(long now) {
        return issue(null, now);
    }

    Issued issue(String username, long now) {
        prune(now);
        String clientId = "client-" + UUID.randomUUID();
        String token = generateToken();
        Session session = new Session(
            clientId,
            username,
            hashToken(token),
            now,
            now + SESSION_LIFETIME_MILLIS
        );
        store(session);
        prune(now);
        return new Issued(token, session);
    }

    Session authenticate(String token, long now) {
        if (!validToken(token)) return null;
        prune(now);
        String clientId = tokenOwners.get(hashToken(token));
        if (clientId == null) return null;
        return sessions.get(clientId);
    }

    String revoke(String token, long now) {
        Session session = authenticate(token, now);
        if (session == null) return null;
        remove(session.clientId());
        return session.clientId();
    }

    void revokeClient(String clientId) {
        remove(clientId);
    }

    boolean hasClient(String clientId) {
        return sessions.containsKey(clientId);
    }

    void load(Properties properties, long now) {
        Set<String> clientIds = new LinkedHashSet<>();
        String prefix = "auth.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            int fieldStart = key.indexOf('.', prefix.length());
            if (fieldStart > prefix.length()) {
                clientIds.add(key.substring(prefix.length(), fieldStart));
            }
        }

        List<Session> loaded = new ArrayList<>();
        for (String clientId : clientIds) {
            Session session = loadSession(properties, clientId);
            if (session != null) loaded.add(session);
        }
        loaded.sort(Comparator.comparingLong(Session::createdAtMillis));

        sessions.clear();
        tokenOwners.clear();
        for (Session session : loaded) store(session);
        prune(now);
    }

    void write(Properties properties) {
        for (Session session : sessions.values()) {
            String prefix = "auth." + session.clientId() + ".";
            properties.setProperty(prefix + "tokenHash", session.tokenHash());
            if (session.username() != null) {
                properties.setProperty(prefix + "username", session.username());
            }
            properties.setProperty(prefix + "createdAtMillis", Long.toString(session.createdAtMillis()));
            properties.setProperty(prefix + "expiresAtMillis", Long.toString(session.expiresAtMillis()));
        }
    }

    void prune(long now) {
        List<String> expired = new ArrayList<>();
        for (Session session : sessions.values()) {
            if (now >= session.expiresAtMillis()) expired.add(session.clientId());
        }
        for (String clientId : expired) remove(clientId);

        while (sessions.size() > MAX_SESSIONS) {
            remove(sessions.keySet().iterator().next());
        }
    }

    private void store(Session session) {
        remove(session.clientId());
        sessions.put(session.clientId(), session);
        tokenOwners.put(session.tokenHash(), session.clientId());
    }

    private void remove(String clientId) {
        Session removed = sessions.remove(clientId);
        if (removed != null) tokenOwners.remove(removed.tokenHash());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{40,128}");
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static Session loadSession(Properties properties, String clientId) {
        if (!clientId.matches("[A-Za-z0-9_-]{1,96}")) return null;
        String prefix = "auth." + clientId + ".";
        try {
            String tokenHash = required(properties, prefix + "tokenHash");
            String username = properties.getProperty(prefix + "username");
            if (username != null) {
                username = normalizeUsername(username);
            }
            long createdAtMillis = Long.parseLong(required(properties, prefix + "createdAtMillis"));
            long expiresAtMillis = Long.parseLong(required(properties, prefix + "expiresAtMillis"));
            if (!tokenHash.matches("[A-Za-z0-9_-]{40,64}") || expiresAtMillis <= createdAtMillis) {
                return null;
            }
            return new Session(clientId, username, tokenHash, createdAtMillis, expiresAtMillis);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizeUsername(String username) {
        String value = username.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("[a-z0-9_-]{3,32}")) {
            throw new IllegalArgumentException("invalid username");
        }
        return value;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value;
    }

    record Issued(String token, Session session) {
    }

    record Session(
        String clientId,
        String username,
        String tokenHash,
        long createdAtMillis,
        long expiresAtMillis
    ) {
    }
}
