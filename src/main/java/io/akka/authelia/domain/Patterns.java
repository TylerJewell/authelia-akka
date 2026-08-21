package io.akka.authelia.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The two questions this capability asks of a pattern beyond matching it. */
final class Patterns {

  private static final Pattern USER_GROUP = Pattern.compile("\\(\\?<(User|Group)>");

  private Patterns() {}

  /** Whether {@code source} declares a capture group named {@code User} or {@code Group}. */
  static boolean declaresSubjectGroup(String source) {
    return USER_GROUP.matcher(source).find();
  }

  /** The text captured by a named group, or null when the pattern has no such group. */
  static String captured(Matcher matcher, String name) {
    try {
      return matcher.group(name);
    } catch (IllegalArgumentException noSuchGroup) {
      return null;
    }
  }
}
