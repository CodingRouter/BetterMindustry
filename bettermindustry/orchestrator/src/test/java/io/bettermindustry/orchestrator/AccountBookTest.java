package io.bettermindustry.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountBookTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void firstAccountIsAdminAndFollowingAccountsArePlayers() {
        AccountBook book = new AccountBook(temporaryDirectory.resolve("accounts.properties"));

        AccountBook.AccountView admin = book.register("Captain", "correct horse battery", 1_000);
        AccountBook.AccountView player = book.register("builder_1", "another secure password", 1_100);

        assertEquals("captain", admin.username());
        assertEquals(Role.ADMIN, admin.role());
        assertEquals(Role.PLAYER, player.role());
    }

    @Test
    void authenticatesOnlyCorrectPasswordAndNeverStoresPlaintext() throws Exception {
        Path stateFile = temporaryDirectory.resolve("accounts.properties");
        AccountBook book = new AccountBook(stateFile);
        book.register("captain", "correct horse battery", 1_000);

        assertNotNull(book.authenticate("CAPTAIN", "correct horse battery"));
        assertNull(book.authenticate("captain", "incorrect password"));

        String state = Files.readString(stateFile, StandardCharsets.UTF_8);
        assertFalse(state.contains("correct horse battery"));
        assertTrue(state.contains("passwordHash"));
        assertTrue(state.contains("salt"));
    }

    @Test
    void restoresAccountsAfterReload() {
        Path stateFile = temporaryDirectory.resolve("accounts.properties");
        AccountBook original = new AccountBook(stateFile);
        original.register("captain", "correct horse battery", 1_000);

        AccountBook restored = new AccountBook(stateFile);
        AccountBook.AccountView account = restored.authenticate("captain", "correct horse battery");

        assertNotNull(account);
        assertEquals(Role.ADMIN, account.role());
    }

    @Test
    void rejectsDuplicateInvalidAndWeakCredentials() {
        AccountBook book = new AccountBook(temporaryDirectory.resolve("accounts.properties"));
        book.register("captain", "correct horse battery", 1_000);

        assertThrows(IllegalArgumentException.class, () ->
            book.register("CAPTAIN", "another secure password", 1_100));
        assertThrows(IllegalArgumentException.class, () ->
            book.register("a", "another secure password", 1_100));
        assertThrows(IllegalArgumentException.class, () ->
            book.register("player_1", "short", 1_100));
    }
}
