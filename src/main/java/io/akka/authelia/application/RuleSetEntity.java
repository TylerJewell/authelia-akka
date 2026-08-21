package io.akka.authelia.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.akka.authelia.domain.Caller;
import io.akka.authelia.domain.Decision;
import io.akka.authelia.domain.QueryTerm;
import io.akka.authelia.domain.Rule;
import io.akka.authelia.domain.RuleSet;
import io.akka.authelia.domain.Target;
import io.akka.authelia.domain.Tier;
import java.util.ArrayList;
import java.util.List;

/**
 * One named rule set. Every append and every evaluation is a command on this entity, so an
 * evaluation reads the rule set as of the command it is queued behind and never sees half
 * an append — SPEC-001 §4 OD-2.
 *
 * <p>A rule's position is assigned here, from the length of the list it is being added to,
 * so position is a fact about the order rules arrived in rather than something a caller can
 * assert (SPEC-001 §3 rule 1).
 */
@Component(id = "rule-set")
public class RuleSetEntity extends EventSourcedEntity<RuleSet, RuleSetEntity.Event> {

  public sealed interface Event {
    @TypeName("default-tier-set")
    record DefaultTierSet(String policy) implements Event {}

    @TypeName("rule-appended")
    record RuleAppended(Rule rule) implements Event {}
  }

  /** A rule as a caller writes it: no position, and the tier still a policy name. */
  public record RuleSpec(
      String policy,
      List<String> domains,
      List<String> domainPatterns,
      List<String> resources,
      List<List<QueryTerm>> query,
      List<String> methods,
      List<String> networks,
      List<List<String>> subjects) {}

  public record Request(Caller caller, String url, String method) {}

  @Override
  public RuleSet emptyState() {
    return new RuleSet(Tier.DENIED, List.of());
  }

  public Effect<String> setDefaultTier(String policy) {
    return effects()
        .persist(new Event.DefaultTierSet(policy))
        .thenReply(set -> set.defaultTier().policyName());
  }

  public Effect<Integer> appendRule(RuleSpec spec) {
    var position = currentState().rules().size() + 1;
    Rule rule;
    try {
      rule =
          Rule.of(
              position,
              Tier.fromPolicyName(spec.policy()),
              spec.domains(),
              spec.domainPatterns(),
              spec.resources(),
              spec.query(),
              spec.methods(),
              spec.networks(),
              spec.subjects());
    } catch (Rule.MalformedRule malformed) {
      // Named, not thrown onwards: an exception the runtime cannot carry between nodes
      // reaches the caller as an opaque failure with no way to tell a typo from an outage.
      // The message names the criterion and not what was written in it.
      return effects().error(malformed.getMessage());
    }

    return effects().persist(new Event.RuleAppended(rule)).thenReply(set -> position);
  }

  /** Derived from state the entity already holds; nothing is written (SPEC-001 §3 rule 16). */
  public ReadOnlyEffect<Decision> evaluate(Request request) {
    var target = target(request);
    return effects().reply(currentState().evaluate(request.caller(), target));
  }

  public ReadOnlyEffect<RuleSet.Trace> trace(Request request) {
    var target = target(request);
    return effects().reply(currentState().trace(request.caller(), target));
  }

  public ReadOnlyEffect<RuleSet> read() {
    return effects().reply(currentState());
  }

  /** A request whose URL cannot be read is refused rather than answered with a tier. */
  private static Target target(Request request) {
    try {
      return Target.of(request.url(), request.method());
    } catch (IllegalArgumentException unreadable) {
      throw new akka.javasdk.CommandException("the requested URL could not be read");
    }
  }

  @Override
  public RuleSet applyEvent(Event event) {
    return switch (event) {
      case Event.DefaultTierSet set ->
          new RuleSet(Tier.fromPolicyName(set.policy()), currentState().rules());
      case Event.RuleAppended appended -> {
        var rules = new ArrayList<>(currentState().rules());
        rules.add(appended.rule());
        yield new RuleSet(currentState().defaultTier(), rules);
      }
    };
  }
}
