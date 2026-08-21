package io.akka.authelia.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered rule set and the walk over it. The first rule that matches decides, and
 * nothing after it is consulted (SPEC-001 §3 rules 1 and 2, question-log rows A1 and A2).
 *
 * <p>The walk never writes: the same rule set answers the same question the same way for
 * as long as it is not appended to (SPEC-001 §3 rule 16).
 */
public record RuleSet(Tier defaultTier, List<Rule> rules) {

  public RuleSet {
    defaultTier = defaultTier == null ? Tier.DENIED : defaultTier;
    rules = rules == null ? List.of() : List.copyOf(rules);
  }

  public Decision evaluate(Caller caller, Target target) {
    for (var i = 0; i < rules.size(); i++) {
      var rule = rules.get(i);
      if (rule.matches(caller, target)) {
        return new Decision(rule.tier(), rule.hasSubjects(), rule.position());
      }
    }
    return new Decision(defaultTier, false, 0);
  }

  /**
   * The same walk, with a row per rule saying why it did or did not decide.
   *
   * <p>The row marked {@code decided} is the rule {@link #evaluate} returned, not a
   * separately computed one — SPEC-001 §4 OD-1. {@code matchesSubjectsExactly} is reported
   * alongside it so a reader can see when a rule decided only because the caller had no
   * identity yet.
   */
  public Trace trace(Caller caller, Target target) {
    var rows = new ArrayList<Row>(rules.size());
    var decision = new Decision(defaultTier, false, 0);
    var settled = false;

    for (var rule : rules) {
      var matched = !settled && rule.matches(caller, target);
      rows.add(
          new Row(
              rule.position(),
              rule.tier(),
              matched,
              !settled,
              rule.matchesDomains(caller, target),
              rule.matchesResources(caller, target),
              rule.matchesQuery(target),
              rule.matchesMethods(target),
              rule.matchesNetworks(caller),
              rule.matchesSubjects(caller),
              rule.matchesSubjectsExactly(caller)));

      if (matched) {
        decision = new Decision(rule.tier(), rule.hasSubjects(), rule.position());
        settled = true;
      }
    }

    return new Trace(decision, List.copyOf(rows));
  }

  /**
   * One rule's part in a decision. {@code reachable} is false once an earlier rule has
   * decided — the criteria on such a row describe a rule that was never consulted.
   */
  public record Row(
      int position,
      Tier tier,
      boolean decided,
      boolean reachable,
      boolean matchesDomains,
      boolean matchesResources,
      boolean matchesQuery,
      boolean matchesMethods,
      boolean matchesNetworks,
      boolean matchesSubjects,
      boolean matchesSubjectsExactly) {}

  public record Trace(Decision decision, List<Row> rows) {}
}
