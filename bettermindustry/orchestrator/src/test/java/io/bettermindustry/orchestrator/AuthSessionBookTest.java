package io.bettermindustry.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSessionBookTest {
    @Test
    void issuesAndAuthenticatesOpaqueSessionToken() {
        AuthSessionBook book = new AuthSessionBook();
        AuthSessionBook.Issued issued = book.issue(1_000);

        AuthSessionBook.Session authenticated = book.authenticate(issued.token(), 1_001);

        assertNotNull(authenticated);
        assertEquals(issued.session().clientId(), authenticated.clientId());
        assertNotEquals(issued.token(), authenticated.tokenHash());
    }

    @Test
    void rejectsUnknownInvalidAndRevokedTokens() {
        AuthSessionBook book = new AuthSessionBook();
        AuthSessionBook.Issued issued = book.issue(1_000);

        assertNull(book.authenticate("invalid", 1_001));
        assertNull(book.authenticate("a".repeat(43), 1_001));
        assertEquals(issued.session().clientId(), book.revoke(issued.token(), 1_002));
        assertNull(book.authenticate(issued.token(), 1_003));
        assertFalse(book.hasClient(issued.session().clientId()));
    }

    @Test
    void persistsOnlyTokenHashAndRestoresAuthentication() {
        AuthSessionBook original = new AuthSessionBook();
        AuthSessionBook.Issued issued = original.issue(1_000);
        Properties properties = new Properties();
        original.write(properties);

        assertFalse(properties.stringPropertyNames().stream()
            .map(properties::getProperty)
            .anyMatch(issued.token()::equals));

        AuthSessionBook restored = new AuthSessionBook();
        restored.load(properties, 2_000);
        AuthSessionBook.Session authenticated = restored.authenticate(issued.token(), 2_001);

        assertNotNull(authenticated);
        assertEquals(issued.session().clientId(), authenticated.clientId());
    }

    @Test
    void persistsAccountUsernameBindingWithSession() {
        AuthSessionBook original = new AuthSessionBook();
        AuthSessionBook.Issued issued = original.issue("captain", "Browser A", 1_000);
        Properties properties = new Properties();
        original.write(properties);

        AuthSessionBook restored = new AuthSessionBook();
        restored.load(properties, 2_000);
        AuthSessionBook.Session authenticated = restored.authenticate(issued.token(), 2_001);

        assertNotNull(authenticated);
        assertEquals("captain", authenticated.username());
        assertEquals("Browser A", authenticated.label());
        assertEquals(1, restored.sessionsFor("captain", 2_001).size());
    }

    @Test
    void expiresSessionsAndKeepsBoundedHistory() {
        AuthSessionBook book = new AuthSessionBook();
        AuthSessionBook.Issued expired = book.issue(1_000);
        assertNull(book.authenticate(
            expired.token(),
            1_000 + AuthSessionBook.SESSION_LIFETIME_MILLIS
        ));

        AuthSessionBook.Issued first = null;
        AuthSessionBook.Issued last = null;
        for (int i = 0; i < AuthSessionBook.MAX_SESSIONS + 1; i++) {
            AuthSessionBook.Issued issued = book.issue(10_000);
            if (i == 0) first = issued;
            last = issued;
        }

        assertNotNull(first);
        assertNotNull(last);
        assertNull(book.authenticate(first.token(), 10_001));
        assertTrue(book.authenticate(last.token(), 10_001) != null);
    }
}
