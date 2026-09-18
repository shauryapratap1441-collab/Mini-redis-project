package com.Shaurya.miniredis;

public class CommandParser {
    private final Storage storage;

    public CommandParser(Storage storage) {
        this.storage = storage;
    }

    public String execute(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "ERR empty command";
        }

        String[] tokens = line.trim().split("\\s+");
        String command = tokens[0].toUpperCase();

        try {
            switch (command) {
                case "SET":
                    if (tokens.length != 3) return "ERR wrong number of arguments for 'set'";
                    storage.set(tokens[1], tokens[2]);
                    return "OK";

                case "GET":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'get'";
                    String val = storage.get(tokens[1]);
                    return val == null ? "(nil)" : val;

                case "LPUSH":
                    if (tokens.length != 3) return "ERR wrong number of arguments for 'lpush'";
                    storage.lpush(tokens[1], tokens[2]);
                    return "OK";

                case "RPUSH":
                    if (tokens.length != 3) return "ERR wrong number of arguments for 'rpush'";
                    storage.rpush(tokens[1], tokens[2]);
                    return "OK";

                case "LPOP":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'lpop'";
                    String popped = storage.lpop(tokens[1]);
                    return popped == null ? "(nil)" : popped;

                case "RPOP":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'rpop'";
                    String rpopped = storage.rpop(tokens[1]);
                    return rpopped == null ? "(nil)" : rpopped;

                case "LRANGE":
                    if (tokens.length != 4) return "ERR wrong number of arguments for 'lrange'";
                    try {
                        int start = Integer.parseInt(tokens[2]);
                        int end = Integer.parseInt(tokens[3]);
                        java.util.List<String> range = storage.lrange(tokens[1], start, end);
                        return range.isEmpty() ? "(empty list)" : String.join(", ", range);
                    } catch (NumberFormatException e) {
                        return "ERR value is not an integer or out of range";
                    }

                case "HSET":
                    if (tokens.length != 4) return "ERR wrong number of arguments for 'hset'";
                    storage.hset(tokens[1], tokens[2], tokens[3]);
                    return "OK";

                case "HGET":
                    if (tokens.length != 3) return "ERR wrong number of arguments for 'hget'";
                    String hval = storage.hget(tokens[1], tokens[2]);
                    return hval == null ? "(nil)" : hval;

                case "HDEL":
                    if (tokens.length != 3) return "ERR wrong number of arguments for 'hdel'";
                    storage.hdel(tokens[1], tokens[2]);
                    return "OK";

                case "HGETALL":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'hgetall'";
                    java.util.HashMap<String, String> all = storage.hgetall(tokens[1]);
                    return all.isEmpty() ? "(empty hash)" : all.toString();

                case "DEL":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'del'";
                    return storage.del(tokens[1]) ? "1" : "0";

                case "EXISTS":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'exists'";
                    return storage.exists(tokens[1]) ? "1" : "0";

                case "EXPIRE":
                    if (tokens.length != 3) return "ERR wrong number of arguments for 'expire'";
                    try {
                        long seconds = Long.parseLong(tokens[2]);
                        return storage.expire(tokens[1], seconds) ? "1" : "0";
                    } catch (NumberFormatException e) {
                        return "ERR value is not an integer or out of range";
                    }

                case "TTL":
                    if (tokens.length != 2) return "ERR wrong number of arguments for 'ttl'";
                    return String.valueOf(storage.ttl(tokens[1]));

                default:
                    return "ERR unknown command '" + command + "'";
            }
        } catch (WrongTypeException e) {
            return "ERR " + e.getMessage();
        }
    }
}