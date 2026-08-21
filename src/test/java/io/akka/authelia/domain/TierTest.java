package io.akka.authelia.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rule 7 — question-log row A6. */
public class TierTest {

  @Test
  void theFourKnownPolicyNames() {
    assertThat(Tier.fromPolicyName("bypass")).isEqualTo(Tier.BYPASS);
    assertThat(Tier.fromPolicyName("one_factor")).isEqualTo(Tier.ONE_FACTOR);
    assertThat(Tier.fromPolicyName("two_factor")).isEqualTo(Tier.TWO_FACTOR);
    assertThat(Tier.fromPolicyName("deny")).isEqualTo(Tier.DENIED);
  }

  @Test
  void unknownPolicyNameBecomesDeny() {
    assertThat(Tier.fromPolicyName("banana")).isEqualTo(Tier.DENIED);
    assertThat(Tier.fromPolicyName("")).isEqualTo(Tier.DENIED);
    assertThat(Tier.fromPolicyName(null)).isEqualTo(Tier.DENIED);
    assertThat(Tier.fromPolicyName("Bypass")).isEqualTo(Tier.DENIED);
  }

  @Test
  void everyTierNamesItselfBackAsThePolicyNameItCameFrom() {
    for (var tier : Tier.values()) {
      assertThat(Tier.fromPolicyName(tier.policyName())).isEqualTo(tier);
    }
  }
}
