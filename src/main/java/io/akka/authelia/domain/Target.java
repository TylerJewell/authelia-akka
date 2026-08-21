package io.akka.authelia.domain;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What is being asked for. {@code path} is what a resource pattern is matched against, and
 * it is not the raw path: dot segments are resolved, a trailing slash survives, and where
 * the request carries a query string it is appended after a {@code ?} (SPEC-001 §3 rule 13,
 * question-log rows A13 and A18).
 */
public record Target(String domain, String path, Map<String, List<String>> query, String method) {

  public Target {
    query = query == null ? Map.of() : Map.copyOf(query);
  }

  public static Target of(String url, String method) {
    var uri = URI.create(url);
    var rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
    var rawQuery = uri.getRawQuery() == null ? "" : uri.getRawQuery();
    var host = uri.getHost() == null ? "" : uri.getHost();

    return new Target(host, matchablePath(rawPath, rawQuery), parseQuery(rawQuery), method);
  }

  private static String matchablePath(String rawPath, String rawQuery) {
    var cleaned = rawPath.length() == 1 ? rawPath : clean(rawPath);
    if (rawQuery.isEmpty()) {
      return cleaned;
    }
    return cleaned + "?" + rawQuery;
  }

  /**
   * Resolves {@code .} and {@code ..} segments, keeping a trailing slash the original had.
   * An attempt to climb above the root stops at the root rather than escaping it.
   */
  private static String clean(String rawPath) {
    var trailing = rawPath.endsWith("/");
    var out = new java.util.ArrayDeque<String>();

    for (var segment : rawPath.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".")) {
        continue;
      }
      if (segment.equals("..")) {
        out.pollLast();
        continue;
      }
      out.addLast(segment);
    }

    if (out.isEmpty()) {
      return "/";
    }
    return "/" + String.join("/", out) + (trailing ? "/" : "");
  }

  private static Map<String, List<String>> parseQuery(String rawQuery) {
    if (rawQuery.isEmpty()) {
      return Map.of();
    }

    Map<String, List<String>> parsed = new LinkedHashMap<>();

    for (var pair : rawQuery.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      var eq = pair.indexOf('=');
      var key = eq < 0 ? pair : pair.substring(0, eq);
      var value = eq < 0 ? "" : pair.substring(eq + 1);
      parsed.computeIfAbsent(decode(key), k -> new java.util.ArrayList<>()).add(decode(value));
    }

    return parsed;
  }

  private static String decode(String s) {
    return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  /** The first value for a key, or the empty string — the shape every query operator wants. */
  public String queryValue(String key) {
    var values = query.get(key);
    return values == null || values.isEmpty() ? "" : values.get(0);
  }

  public boolean hasQueryKey(String key) {
    return query.containsKey(key);
  }
}
