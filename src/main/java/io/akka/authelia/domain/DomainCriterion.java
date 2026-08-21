package io.akka.authelia.domain;

import java.util.Locale;

/**
 * A domain literal, in one of four forms: an exact name, a {@code *.} suffix, or a
 * {@code {user}} or {@code {group}} placeholder followed by a suffix (SPEC-001 §3 rules 11
 * and 14, question-log rows A10 and A11).
 *
 * <p>The literal reaches here already lower-cased by {@link Rule#of}.
 */
final class DomainCriterion {

  private DomainCriterion() {}

  static boolean matches(String literal, String domain, Caller caller) {
    if (literal.startsWith("*.")) {
      return endsWithIgnoringCase(domain, literal.substring(1));
    }
    if (literal.startsWith("{user}")) {
      var suffix = literal.substring("{user}".length());
      if (caller.isAnonymous()) {
        return hasNonEmptyFirstLabel(domain, suffix);
      }
      return domain.equals(caller.username() + suffix);
    }
    if (literal.startsWith("{group}")) {
      var suffix = literal.substring("{group}".length());
      if (caller.isAnonymous()) {
        return hasNonEmptyFirstLabel(domain, suffix);
      }
      var dot = domain.indexOf('.');
      if (dot < 0) {
        return false;
      }
      return domain.substring(dot).equals(suffix) && caller.hasGroupIgnoringCase(domain.substring(0, dot));
    }
    return domain.equalsIgnoreCase(literal);
  }

  private static boolean hasNonEmptyFirstLabel(String domain, String suffix) {
    return endsWithIgnoringCase(domain, suffix) && domain.length() > suffix.length();
  }

  private static boolean endsWithIgnoringCase(String domain, String suffix) {
    return domain.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT));
  }
}
