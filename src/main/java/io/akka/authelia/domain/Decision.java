package io.akka.authelia.domain;

/**
 * What a caller has to reach, which rule said so, and whether the answer still depends on
 * who the caller turns out to be.
 *
 * <p>{@code position} is 0 when no rule matched and the set's default tier applies.
 * {@code provisional} means the deciding rule named subjects, so identifying the caller may
 * move the answer to a different rule and a different tier (SPEC-001 §3 rule 10).
 */
public record Decision(Tier tier, boolean provisional, int position) {}
