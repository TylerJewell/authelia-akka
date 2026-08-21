package io.akka.authelia.domain;

/**
 * How far a caller has to authenticate before a request is allowed, from least to most
 * demanding, with {@link #DENIED} meaning no amount of authenticating suffices.
 *
 * <p>SPEC-001 §2, §3 rule 7.
 */
public enum Tier {
  BYPASS("bypass"),
  ONE_FACTOR("one_factor"),
  TWO_FACTOR("two_factor"),
  DENIED("deny");

  private final String policyName;

  Tier(String policyName) {
    this.policyName = policyName;
  }

  public String policyName() {
    return policyName;
  }

  /** Any name that is not one of the four, including none at all, is a refusal. */
  public static Tier fromPolicyName(String name) {
    for (var tier : values()) {
      if (tier.policyName.equals(name)) {
        return tier;
      }
    }
    return DENIED;
  }
}
