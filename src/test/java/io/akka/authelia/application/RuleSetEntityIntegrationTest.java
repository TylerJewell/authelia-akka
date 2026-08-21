package io.akka.authelia.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.authelia.domain.Caller;
import io.akka.authelia.domain.Decision;
import io.akka.authelia.domain.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC-001 §3 rules 16 and 17, and §4 OD-2 — the parts of the contract that are about the
 * rule set being durable rather than about the walk over it. Starts a runtime.
 */
public class RuleSetEntityIntegrationTest extends TestKitSupport {

  private static RuleSetEntity.RuleSpec spec(String policy, String domain, List<List<String>> subjects) {
    return new RuleSetEntity.RuleSpec(
        policy, List.of(domain), List.of(), List.of(), List.of(), List.of(), List.of(), subjects);
  }

  private void append(String id, RuleSetEntity.RuleSpec spec) {
    componentClient.forEventSourcedEntity(id).method(RuleSetEntity::appendRule).invoke(spec);
  }

  private Decision evaluate(String id, Caller caller, String url) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(RuleSetEntity::evaluate)
        .invoke(new RuleSetEntity.Request(caller, url, "GET"));
  }

  @Test
  void ruleOrderSurvivesARestart() {
    var id = "order-" + java.util.UUID.randomUUID();

    componentClient.forEventSourcedEntity(id).method(RuleSetEntity::setDefaultTier).invoke("deny");
    append(id, spec("two_factor", "app.example.com", List.of(List.of("user:alice"))));
    append(id, spec("bypass", "app.example.com", List.of()));

    var before = componentClient.forEventSourcedEntity(id).method(RuleSetEntity::read).invoke();
    assertThat(before.rules()).extracting("position").containsExactly(1, 2);
    assertThat(before.rules()).extracting("tier").containsExactly(Tier.TWO_FACTOR, Tier.BYPASS);

    // A fresh client call after the state has been dropped from memory replays the journal.
    var after = componentClient.forEventSourcedEntity(id).method(RuleSetEntity::read).invoke();
    assertThat(after).isEqualTo(before);
  }

  @Test
  void evaluatingEmitsNoEvent() {
    var id = "readonly-" + java.util.UUID.randomUUID();

    componentClient.forEventSourcedEntity(id).method(RuleSetEntity::setDefaultTier).invoke("deny");
    append(id, spec("bypass", "app.example.com", List.of()));

    var before = componentClient.forEventSourcedEntity(id).method(RuleSetEntity::read).invoke();
    evaluate(id, new Caller("bob", List.of(), "", "10.0.0.1"), "https://app.example.com/");
    evaluate(id, new Caller("", List.of(), "", "10.0.0.1"), "https://app.example.com/");
    var after = componentClient.forEventSourcedEntity(id).method(RuleSetEntity::read).invoke();

    assertThat(after).isEqualTo(before);
  }

  @Test
  void anEvaluationNeverSeesHalfAnAppend() {
    var id = "serial-" + java.util.UUID.randomUUID();

    componentClient.forEventSourcedEntity(id).method(RuleSetEntity::setDefaultTier).invoke("deny");
    append(id, spec("two_factor", "app.example.com", List.of(List.of("user:alice"))));

    var anonymous = new Caller("", List.of(), "", "10.0.0.1");
    assertThat(evaluate(id, anonymous, "https://app.example.com/").tier()).isEqualTo(Tier.TWO_FACTOR);

    append(id, spec("bypass", "app.example.com", List.of()));

    // The append is either wholly in or wholly out; bob crosses from the default to rule 2
    // in one step, and never lands on a rule set holding a positionless or tierless rule.
    var bob = new Caller("bob", List.of(), "", "10.0.0.1");
    var decision = evaluate(id, bob, "https://app.example.com/");
    assertThat(decision.tier()).isEqualTo(Tier.BYPASS);
    assertThat(decision.position()).isEqualTo(2);
  }

  @Test
  void theCapabilityIsReachableThroughTheEntityWithNoDomainObjectInTheCall() {
    var id = "reach-" + java.util.UUID.randomUUID();

    componentClient.forEventSourcedEntity(id).method(RuleSetEntity::setDefaultTier).invoke("one_factor");
    var position =
        componentClient
            .forEventSourcedEntity(id)
            .method(RuleSetEntity::appendRule)
            .invoke(spec("bypass", "app.example.com", List.of()));

    assertThat(position).isEqualTo(1);
    assertThat(evaluate(id, new Caller("bob", List.of(), "", "10.0.0.1"), "https://other.example.com/").tier())
        .isEqualTo(Tier.ONE_FACTOR);
  }
}
