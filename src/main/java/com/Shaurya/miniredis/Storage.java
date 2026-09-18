package com.Shaurya.miniredis;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
public class Storage {
    private final Map<String,Object> data = new HashMap<>();
    private final Map<String,Long> expiry = new HashMap<>();

    /**
     * Lazily checks whether a key has expired. If it has, removes it from
     * both maps and returns true. This is the same approach real Redis uses
     * for most keys: no background scanning thread, just a check on access.
     */
    private synchronized boolean isExpired(String key) {
        Long expiresAt = expiry.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiresAt) {
            data.remove(key);
            expiry.remove(key);
            return true;
        }
        return false;
    }

    public synchronized void set(String key,String value){
        data.put(key,value);
        expiry.remove(key); // SET always clears any existing TTL, matching real Redis
    }

    public synchronized String get(String key){
        if (isExpired(key)) return null;
        Object value = data.get(key);
        if(value == null)
            return null;
        if(!(value instanceof String)){
            throw new WrongTypeException("value at key "+key+" is not a String");
        }
        return (String) value ;
    }

    public synchronized void lpush(String key,String value){
        isExpired(key);
        Object existing = data.get(key);
        LinkedList<String> list;
        if(existing == null){
            list = new LinkedList<>();
            data.put(key,list);
        }
        else if(existing instanceof LinkedList){
            list = (LinkedList<String>) existing;
        }
        else{
            throw new WrongTypeException("value at key '" + key + "' is not a list");
        }
        list.addFirst(value);
    }

    public synchronized void rpush(String key,String value) {
        isExpired(key);
        Object existing = data.get(key);
        LinkedList<String> list;
        if (existing == null) {
            list = new LinkedList<>();
            data.put(key, list);
        } else if (existing instanceof LinkedList) {
            list = (LinkedList<String>) existing;
        } else {
            throw new WrongTypeException("value at key '" + key + "' is not a list");
        }
        list.addLast(value);
    }

    public synchronized java.util.List<String> lrange(String key, int start, int end) {
        if (isExpired(key)) return new LinkedList<>();
        Object existing = data.get(key);
        if (existing == null) {
            return new LinkedList<>();
        }
        if (!(existing instanceof LinkedList)) {
            throw new WrongTypeException("value at key '" + key + "' is not a list");
        }
        LinkedList<String> list = (LinkedList<String>) existing;

        int size = list.size();
        int from = Math.max(start, 0);
        int to = Math.min(end, size - 1);

        if (from > to || size == 0) {
            return new LinkedList<>();
        }

        return new LinkedList<>(list.subList(from, to + 1));
    }

    private synchronized String popFrom(String key, boolean fromFront) {
        if (isExpired(key)) return null;
        Object existing = data.get(key);
        if (existing == null) {
            return null;
        }
        if (!(existing instanceof LinkedList)) {
            throw new WrongTypeException("value at key '" + key + "' is not a list");
        }
        LinkedList<String> list = (LinkedList<String>) existing;
        if (list.isEmpty()) {
            return null;
        }
        String value = fromFront ? list.removeFirst() : list.removeLast();
        if (list.isEmpty()) {
            data.remove(key);
        }
        return value;
    }

    public String lpop(String key) {
        return popFrom(key, true);
    }

    public String rpop(String key) {
        return popFrom(key, false);
    }

    public synchronized void hset(String key, String field, String value) {
        isExpired(key);
        Object existing = data.get(key);
        HashMap<String, String> hash;
        if (existing == null) {
            hash = new HashMap<>();
            data.put(key, hash);
        } else if (existing instanceof HashMap) {
            hash = (HashMap<String, String>) existing;
        } else {
            throw new WrongTypeException("value at key '" + key + "' is not a hash");
        }
        hash.put(field, value);
    }

    public synchronized String hget(String key, String field) {
        if (isExpired(key)) return null;
        Object existing = data.get(key);
        if (existing == null) {
            return null;
        }
        if (!(existing instanceof HashMap)) {
            throw new WrongTypeException("value at key '" + key + "' is not a hash");
        }
        HashMap<String, String> hash = (HashMap<String, String>) existing;
        return hash.get(field);
    }

    public synchronized void hdel(String key, String field) {
        if (isExpired(key)) return;
        Object existing = data.get(key);
        if (existing == null) {
            return;
        }
        if (!(existing instanceof HashMap)) {
            throw new WrongTypeException("value at key '" + key + "' is not a hash");
        }
        HashMap<String, String> hash = (HashMap<String, String>) existing;
        hash.remove(field);
        if (hash.isEmpty()) {
            data.remove(key);
        }
    }

    public synchronized HashMap<String, String> hgetall(String key) {
        if (isExpired(key)) return new HashMap<>();
        Object existing = data.get(key);
        if (existing == null) {
            return new HashMap<>();
        }
        if (!(existing instanceof HashMap)) {
            throw new WrongTypeException("value at key '" + key + "' is not a hash");
        }
        return (HashMap<String, String>) existing;
    }

    /** Deletes a key of any type. Returns true if a key was actually removed. */
    public synchronized boolean del(String key) {
        if (isExpired(key)) return false; // already gone, nothing to delete
        boolean existed = data.remove(key) != null;
        expiry.remove(key);
        return existed;
    }

    /** Returns true if the key exists and hasn't expired. */
    public synchronized boolean exists(String key) {
        if (isExpired(key)) return false;
        return data.containsKey(key);
    }

    /**
     * Sets a TTL (in seconds) on an existing key. Returns true if the key
     * existed and the TTL was set, false if the key doesn't exist.
     */
    public synchronized boolean expire(String key, long seconds) {
        if (isExpired(key)) return false;
        if (!data.containsKey(key)) return false;
        expiry.put(key, System.currentTimeMillis() + seconds * 1000);
        return true;
    }

    /**
     * Returns remaining TTL in seconds, matching real Redis conventions:
     * -2 if the key doesn't exist, -1 if it exists but has no TTL set,
     * otherwise the remaining seconds (rounded up).
     */
    public synchronized long ttl(String key) {
        if (isExpired(key)) return -2;
        if (!data.containsKey(key)) return -2;
        Long expiresAt = expiry.get(key);
        if (expiresAt == null) return -1;
        long remainingMs = expiresAt - System.currentTimeMillis();
        return (long) Math.ceil(remainingMs / 1000.0);
    }

    public synchronized void saveSnapshot(String filepath) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filepath))) {
            out.writeObject(data);
            out.writeObject(expiry);
            System.out.println("Snapshot saved to " + new File(filepath).getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Failed to save snapshot: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void loadSnapshot(String filepath) {
        File file = new File(filepath);
        if (!file.exists()) {
            return; // nothing to load yet, fresh start
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filepath))) {
            Map<String, Object> loadedData = (Map<String, Object>) in.readObject();
            data.clear();
            data.putAll(loadedData);

            // Older snapshots (saved before TTL support existed) won't have
            // a second object, so treat that as "no expiries recorded".
            try {
                Map<String, Long> loadedExpiry = (Map<String, Long>) in.readObject();
                expiry.clear();
                expiry.putAll(loadedExpiry);
            } catch (IOException e) {
                expiry.clear();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Failed to load snapshot: " + e.getMessage());
        }
    }
}