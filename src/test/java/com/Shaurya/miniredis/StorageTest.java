package com.Shaurya.miniredis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageTest {

    private Storage storage;

    @BeforeEach
    void setUp() {
        storage = new Storage();
    }

    // ---- Strings ----

    @Test
    void setAndGet() {
        storage.set("name", "gemini");
        assertEquals("gemini", storage.get("name"));
    }

    @Test
    void getOnMissingKeyReturnsNull() {
        assertNull(storage.get("missing"));
    }

    @Test
    void getOnListKeyThrowsWrongType() {
        storage.lpush("mylist", "a");
        assertThrows(WrongTypeException.class, () -> storage.get("mylist"));
    }

    @Test
    void setOverwritesRegardlessOfPriorType() {
        storage.lpush("key", "a");
        storage.set("key", "now a string");
        assertEquals("now a string", storage.get("key"));
    }

    // ---- Lists ----

    @Test
    void lpushAndRpushOrdering() {
        storage.lpush("list", "b");
        storage.lpush("list", "a"); // now: a, b
        storage.rpush("list", "c"); // now: a, b, c
        // Note: this project's LRANGE only supports non-negative indices
        // (a deliberate scope cut from real Redis's -1 = "last element"),
        // so we pass an end index large enough to cover the whole list.
        assertEquals(List.of("a", "b", "c"), storage.lrange("list", 0, 100));
    }

    @Test
    void lrangeOnMissingKeyReturnsEmptyList() {
        assertTrue(storage.lrange("missing", 0, 10).isEmpty());
    }

    @Test
    void lpopRemovesKeyOnceListIsEmpty() {
        storage.lpush("list", "only");
        assertEquals("only", storage.lpop("list"));
        assertFalse(storage.exists("list"));
    }

    @Test
    void lpopOnMissingKeyReturnsNull() {
        assertNull(storage.lpop("missing"));
    }

    // ---- Hashes ----

    @Test
    void hsetAndHgetall() {
        storage.hset("user:1", "name", "alice");
        storage.hset("user:1", "age", "30");
        var all = storage.hgetall("user:1");
        assertEquals("alice", all.get("name"));
        assertEquals("30", all.get("age"));
    }

    @Test
    void hdelRemovesKeyOnceHashIsEmpty() {
        storage.hset("user:1", "name", "alice");
        storage.hdel("user:1", "name");
        assertFalse(storage.exists("user:1"));
    }

    @Test
    void hgetOnMissingFieldReturnsNull() {
        storage.hset("user:1", "name", "alice");
        assertNull(storage.hget("user:1", "email"));
    }

    // ---- DEL / EXISTS ----

    @Test
    void delRemovesKeyAndReturnsTrue() {
        storage.set("key", "value");
        assertTrue(storage.del("key"));
        assertFalse(storage.exists("key"));
    }

    @Test
    void delOnMissingKeyReturnsFalse() {
        assertFalse(storage.del("missing"));
    }

    @Test
    void existsReflectsCurrentState() {
        assertFalse(storage.exists("key"));
        storage.set("key", "value");
        assertTrue(storage.exists("key"));
    }

    // ---- TTL / EXPIRE ----

    @Test
    void ttlOnKeyWithNoExpiryIsMinusOne() {
        storage.set("key", "value");
        assertEquals(-1, storage.ttl("key"));
    }

    @Test
    void ttlOnMissingKeyIsMinusTwo() {
        assertEquals(-2, storage.ttl("missing"));
    }

    @Test
    void expireOnMissingKeyReturnsFalse() {
        assertFalse(storage.expire("missing", 10));
    }

    @Test
    void expireSetsPositiveTtl() {
        storage.set("key", "value");
        assertTrue(storage.expire("key", 10));
        long ttl = storage.ttl("key");
        assertTrue(ttl > 0 && ttl <= 10);
    }

    @Test
    void keyExpiresAfterTtlElapses() throws InterruptedException {
        storage.set("key", "value");
        storage.expire("key", 1); // 1 second TTL
        Thread.sleep(1100);
        assertNull(storage.get("key"));
        assertFalse(storage.exists("key"));
    }

    @Test
    void setClearsExistingTtl() {
        storage.set("key", "value");
        storage.expire("key", 10);
        storage.set("key", "new value"); // should clear the TTL
        assertEquals(-1, storage.ttl("key"));
    }
}
