package io.akka.authelia.domain;

import static io.akka.authelia.domain.Fixtures.get;
import static io.akka.authelia.domain.Fixtures.rule;
import static io.akka.authelia.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 1 and 2 — question-log rows A1, A2. */
public class RuleSetEvaluationTest {

  @Test
  void firstMatchingRuleByPositionDecides() {
    var set =
        new RuleSet(
            Tier.DENIED,
            List.of(
                rule(1, Tier.TWO_FACTOR).domains("app.example.com").build(),
                rule(2, Tier.BYPASS).domains("app.example.com").build()));

    var decision = set.evaluate(user("bob", "10.0.0.1"), get("https://app.example.com/"));

    assertThat(decision.tier()).isEqualTo(Tier.TWO_FACTOR);
    assertThat(decision.position()).isEqualTo(1);
  }

  @Test
  void aRuleThatDoesNotMatchIsSteppedOverRatherThanEndingTheWalk() {
    var set =
        new RuleSet(
            Tier.DENIED,
            List.of(
                rule(1, Tier.TWO_FACTOR).domains("other.example.com").build(),
                rule(2, Tier.BYPASS).domains("app.example.com").build()));

    var decision = set.evaluate(user("bob", "10.0.0.1"), get("https://app.example.com/"));

    assertThat(decision.tier()).isEqualTo(Tier.BYPASS);
    assertThat(decision.position()).isEqualTo(2);
  }

  @Test
  void defaultTierWhenNoRuleMatches() {
    var set =
        new RuleSet(
            Tier.ONE_FACTOR, List.of(rule(1, Tier.BYPASS).domains("app.example.com").build()));

    var decision = set.evaluate(user("bob", "10.0.0.1"), get("https://other.example.com/"));

    assertThat(decision.tier()).isEqualTo(Tier.ONE_FACTOR);
    assertThat(decision.provisional()).isFalse();
    assertThat(decision.position()).isZero();
  }

  @Test
  void reorderingTheSameTwoRulesChangesTheAnswer() {
    var strict = rule(1, Tier.TWO_FACTOR).domains("app.example.com").build();
    var loose = rule(2, Tier.BYPASS).domains("app.example.com").build();
    var caller = user("bob", "10.0.0.1");
    var target = get("https://app.example.com/");

    assertThat(new RuleSet(Tier.DENIED, List.of(strict, loose)).evaluate(caller, target).tier())
        .isEqualTo(Tier.TWO_FACTOR);
    assertThat(
            new RuleSet(
                    Tier.DENIED,
                    List.of(
                        rule(1, Tier.BYPASS).domains("app.example.com").build(),
                        rule(2, Tier.TWO_FACTOR).domains("app.example.com").build()))
                .evaluate(caller, target)
                .tier())
        .isEqualTo(Tier.BYPASS);
  }

  @Test
  void evaluatingDoesNotChangeTheRuleSet() {
    var rules =
        List.of(
            rule(1, Tier.TWO_FACTOR).domains("app.example.com").build(),
            rule(2, Tier.BYPASS).domains("app.example.com").build());
    var set = new RuleSet(Tier.DENIED, rules);

    set.evaluate(user("bob", "10.0.0.1"), get("https://app.example.com/"));

    assertThat(set.rules()).isEqualTo(rules);
  }
}
