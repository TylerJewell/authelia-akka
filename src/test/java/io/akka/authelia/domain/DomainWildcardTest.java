package io.akka.authelia.domain;

import static io.akka.authelia.domain.Fixtures.anonymous;
import static io.akka.authelia.domain.Fixtures.get;
import static io.akka.authelia.domain.Fixtures.rule;
import static io.akka.authelia.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rule 14 and the domain half of rule 10 — question-log rows A9, A10. */
public class DomainWildcardTest {

  private static final RuleSet SET =
      new RuleSet(
          Tier.ONE_FACTOR,
          List.of(
              rule(1, Tier.ONE_FACTOR).domains("{user}.example.com").build(),
              rule(2, Tier.TWO_FACTOR).domains("{group}.example.com").build(),
              rule(3, Tier.BYPASS).domains("*.example.com").build()));

  @Test
  void aUserWildcardMatchesTheCallersOwnSubdomain() {
    var decision = SET.evaluate(user("alice", "10.0.0.1"), get("https://alice.example.com/"));

    assertThat(decision.position()).isEqualTo(1);
    assertThat(decision.tier()).isEqualTo(Tier.ONE_FACTOR);
    assertThat(decision.provisional()).isTrue();
  }

  @Test
  void aUserWildcardDoesNotMatchSomebodyElsesSubdomain() {
    var decision = SET.evaluate(user("bob", "10.0.0.1", "alice"), get("https://alice.example.com/"));

    assertThat(decision.position()).isEqualTo(2);
    assertThat(decision.tier()).isEqualTo(Tier.TWO_FACTOR);
  }

  @Test
  void bothWildcardsMatchLooselyForAnAnonymousCaller() {
    var decision = SET.evaluate(anonymous("10.0.0.1"), get("https://anyone.example.com/"));

    assertThat(decision.position()).isEqualTo(1);
    assertThat(decision.provisional()).isTrue();
  }

  @Test
  void aWildcardNeedsANonEmptyFirstLabelEvenAnonymously() {
    var decision = SET.evaluate(anonymous("10.0.0.1"), get("https://example.com/"));

    assertThat(decision.position()).isZero();
    assertThat(decision.tier()).isEqualTo(Tier.ONE_FACTOR);
  }

  @Test
  void aWildcardDomainWithoutASubjectPlaceholderIsNotProvisional() {
    var set = new RuleSet(Tier.DENIED, List.of(rule(1, Tier.BYPASS).domains("*.example.com").build()));

    var decision = set.evaluate(anonymous("10.0.0.1"), get("https://anything.example.com/"));

    assertThat(decision.tier()).isEqualTo(Tier.BYPASS);
    assertThat(decision.provisional()).isFalse();
  }

  @Test
  void aNamedCaptureGroupInAPatternMakesTheRuleProvisional() {
    var byDomain = rule(1, Tier.BYPASS).domainPatterns("^(?<User>[a-z]+)\\.example\\.com$").build();
    var byResource = rule(1, Tier.BYPASS).resources("^/home/(?<Group>[a-z]+)/").build();
    var plain = rule(1, Tier.BYPASS).domainPatterns("^[a-z]+\\.example\\.com$").build();

    assertThat(byDomain.hasSubjects()).isTrue();
    assertThat(byResource.hasSubjects()).isTrue();
    assertThat(plain.hasSubjects()).isFalse();
  }

  @Test
  void aCaptureGroupPatternMatchesTheCallersOwnValueWhenIdentified() {
    var set =
        new RuleSet(
            Tier.DENIED,
            List.of(rule(1, Tier.BYPASS).domainPatterns("^(?<User>[a-z]+)\\.example\\.com$").build()));

    assertThat(set.evaluate(user("alice", "10.0.0.1"), get("https://alice.example.com/")).tier())
        .isEqualTo(Tier.BYPASS);
    assertThat(set.evaluate(user("bob", "10.0.0.1"), get("https://alice.example.com/")).tier())
        .isEqualTo(Tier.DENIED);
    assertThat(set.evaluate(anonymous("10.0.0.1"), get("https://alice.example.com/")).tier())
        .isEqualTo(Tier.BYPASS);
  }

  @Test
  void aCaptureGroupComparisonIgnoresCase() {
    var set =
        new RuleSet(
            Tier.DENIED,
            List.of(
                rule(1, Tier.BYPASS).domainPatterns("^(?<User>[a-z]+)\\.example\\.com$").build(),
                rule(2, Tier.ONE_FACTOR)
                    .domains("r.example.com")
                    .resources("^/home/(?<Group>[a-z]+)/")
                    .build()));

    assertThat(set.evaluate(user("ALICE", "10.0.0.1"), get("https://alice.example.com/")).tier())
        .isEqualTo(Tier.BYPASS);
    assertThat(set.evaluate(user("zed", "10.0.0.1", "OPS"), get("https://r.example.com/home/ops/x")).tier())
        .isEqualTo(Tier.ONE_FACTOR);
  }

  @Test
  void aCaptureGroupPatternIsSearchedRatherThanAnchored() {
    var set =
        new RuleSet(
            Tier.DENIED,
            List.of(rule(1, Tier.BYPASS).resources("/home/(?<User>[a-z]+)/").build()));

    assertThat(set.evaluate(user("alice", "10.0.0.1"), get("https://x.example.com/srv/home/alice/f")).tier())
        .isEqualTo(Tier.BYPASS);
  }
}
