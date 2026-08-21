package io.akka.authelia.domain;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One condition on a query parameter. Six operators, in three pairs, each pair a test and
 * its negation (SPEC-001 §2, question-log row A5).
 *
 * <p>An operator outside the six never matches. The source drops such a term when it builds
 * the rule, which quietly widens the rule; refusing to match keeps the term visible instead
 * — see the published README's list of differences.
 *
 * <p>{@code compiled} is {@code value} as a pattern, and null for the four operators that
 * do not take one. It is a component rather than something worked out inside
 * {@link #matches} so that the regular expression compiler stays out of the path a request
 * takes.
 *
 * <p>It is derived from the other two components rather than accepted, so a term built in
 * code and a term read back from what a caller sent hold the same pattern. Accepting it
 * would make a term that arrived without one — which is every term written over the
 * interface — carry no pattern at all while still claiming an operator that needs one.
 */
public record QueryTerm(String operator, String key, String value, Pattern compiled) {

  public QueryTerm {
    compiled = compile(operator, value);
  }

  public QueryTerm(String operator, String key, String value) {
    this(operator, key, value, null);
  }

  public static QueryTerm of(String operator, String key, String value) {
    return new QueryTerm(operator, key, value);
  }

  private static Pattern compile(String operator, String value) {
    if (value == null || !(operator.equals("pattern") || operator.equals("not pattern"))) {
      return null;
    }
    try {
      return Pattern.compile(value);
    } catch (PatternSyntaxException unusable) {
      return null;
    }
  }

  /** Refuses a term whose operator wants a pattern and whose text will not compile. */
  void validate(int position) {
    if (compiled == null && (operator.equals("pattern") || operator.equals("not pattern"))) {
      throw new Rule.MalformedRule(
          "rule " + position + " has a query pattern that is not a valid regular expression");
    }
  }

  public boolean matches(Target target) {
    return switch (operator) {
      case "present" -> target.hasQueryKey(key);
      case "absent" -> !target.hasQueryKey(key);
      case "equal" -> target.queryValue(key).equals(value);
      case "not equal" -> !target.queryValue(key).equals(value);
      case "pattern" -> compiled.matcher(target.queryValue(key)).find();
      case "not pattern" -> !compiled.matcher(target.queryValue(key)).find();
      default -> false;
    };
  }
}
