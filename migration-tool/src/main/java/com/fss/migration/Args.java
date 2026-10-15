package com.fss.migration;

import java.util.HashMap;
import java.util.Map;

/** {@code --key value} / {@code --flag} parser with environment-variable fallback. */
final class Args {
    private final Map<String, String> values = new HashMap<>();

    Args(String[] args, int from) {
        for (int i = from; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) throw new IllegalArgumentException("Unexpected argument: " + arg);
            String key = arg.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                values.put(key, args[++i]);
            } else {
                values.put(key, "true");
            }
        }
    }

    String get(String key, String env) {
        String value = values.get(key);
        if (value == null && env != null) value = System.getenv(env);
        return value == null || value.isBlank() ? null : value;
    }

    String require(String key, String env) {
        String value = get(key, env);
        if (value == null) {
            throw new IllegalArgumentException("Missing --" + key + (env == null ? "" : " (or env " + env + ")"));
        }
        return value;
    }

    boolean flag(String key) {
        return Boolean.parseBoolean(values.getOrDefault(key, "false"));
    }
}
