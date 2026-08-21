package io.akka.authelia.domain;

import static io.akka.authelia.domain.Fixtures.anonymous;
import static io.akka.authelia.domain.Fixtures.get;
import static io.akka.authelia.domain.Fixtures.rule;
import static io.akka.authelia.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The behaviour this port exists for: SPEC-001 §3 rules 8, 9 and 10 — question-log rows
 * A7, A8, A9, A15. One object, one rule set, and a tier that moves with who is asking.
 */
public class AnonymousTierTest {

  private static final RuleSet SET =
      new RuleSet(
          Tier.DENIED,
          List.of(
              rule(1, Tier.TWO_FACTOR)
                  .domains("app.example.com")
                  .subjects(List.of(List.of("user:alice")))
                  .build(),
              rule(2, Tier.BYPASS).domains("app.example.com").build()));

  private static final Target APP = get("https://app.example.com/");

  @Test
  void anonymousCallerMatchesARuleThatNamesOtherSubjects() {
    var decision = SET.evaluate(anonymous("10.0.0.1"), APP);

    assertThat(decision.tier()).isEqualTo(Tier.TWO_FACTOR);
    assertThat(decision.position()).isEqualTo(1);
    assertThat(decision.provisional()).isTrue();
  }

  @Test
  void identifiedCallerFallsThroughToTheNextRule() {
    var decision = SET.evaluate(user("bob", "10.0.0.1", "users"), APP);

    assertThat(decision.tier()).isEqualTo(Tier.BYPASS);
    assertThat(decision.position()).isEqualTo(2);
  }

  @Test
  void theNamedSubjectStillGetsTheStrictTier() {
    assertThat(SET.evaluate(user("alice", "10.0.0.1"), APP).tier()).isEqualTo(Tier.TWO_FACTOR);
  }

  @Test
  void provisionalIsSetByTheDecidingRuleAlone() {
    assertThat(SET.evaluate(user("bob", "10.0.0.1"), APP).provisional()).isFalse();
    assertThat(SET.evaluate(user("alice", "10.0.0.1"), APP).provisional()).isTrue();
  }

  @Test
  void anonymousCanReachALooserTierThanAnyIdentifiedCallerWould() {
    var set =
        new RuleSet(
            Tier.DENIED,
            List.of(
                rule(1, Tier.BYPASS)
                    .domains("o.example.com")
                    .subjects(List.of(List.of("oauth2:client:cli1")))
                    .build(),
                rule(2, Tier.DENIED).domains("o.example.com").build()));
    var target = get("https://o.example.com/");

    assertThat(set.evaluate(anonymous("10.0.0.1"), target).tier()).isEqualTo(Tier.BYPASS);
    assertThat(set.evaluate(new Caller("", List.of(), "cli1", "10.0.0.1"), target).tier())
        .isEqualTo(Tier.BYPASS);
    assertThat(set.evaluate(new Caller("", List.of(), "cli2", "10.0.0.1"), target).tier())
        .isEqualTo(Tier.DENIED);
  }

  @Test
  void aClientIdAloneMakesACallerIdentified() {
    assertThat(new Caller("", List.of(), "", "10.0.0.1").isAnonymous()).isTrue();
    assertThat(new Caller("", List.of(), "cli1", "10.0.0.1").isAnonymous()).isFalse();
    assertThat(new Caller("", List.of("g"), "", "10.0.0.1").isAnonymous()).isFalse();
    assertThat(new Caller("u", List.of(), "", "10.0.0.1").isAnonymous()).isFalse();
  }

  @Test
  void everyEntryInASubjectGroupMustMatchAndTheGroupsAreAlternatives() {
    var set =
        new RuleSet(
            Tier.ONE_FACTOR,
            List.of(
                rule(1, Tier.TWO_FACTOR)
                    .domains("s.example.com")
                    .subjects(List.of(List.of("user:alice", "group:admins"), List.of("group:ops")))
                    .build()));
    var target = get("https://s.example.com/");

    assertThat(set.evaluate(user("alice", "10.0.0.1", "admins"), target).tier()).isEqualTo(Tier.TWO_FACTOR);
    assertThat(set.evaluate(user("alice", "10.0.0.1"), target).tier()).isEqualTo(Tier.ONE_FACTOR);
    assertThat(set.evaluate(user("zed", "10.0.0.1", "ops"), target).tier()).isEqualTo(Tier.TWO_FACTOR);
  }

  @Test
  void aSubjectEntryWithNoRecognisedPrefixIsDropped() {
    var withJunk =
        rule(1, Tier.TWO_FACTOR)
            .domains("s.example.com")
            .subjects(List.of(List.of("alice")))
            .build();

    assertThat(withJunk.subjects()).isEmpty();
    assertThat(withJunk.hasSubjects()).isFalse();
  }

  @Test
  void theTraceNamesTheRuleThatDecided() {
    var trace = SET.trace(anonymous("10.0.0.1"), APP);

    assertThat(trace.decision().position()).isEqualTo(1);
    assertThat(trace.rows()).hasSize(2);
    assertThat(trace.rows().get(0).decided()).isTrue();
    assertThat(trace.rows().get(0).matchesSubjectsExactly()).isFalse();
    assertThat(trace.rows().get(1).decided()).isFalse();
    assertThat(trace.rows().get(1).reachable()).isFalse();
  }
}
