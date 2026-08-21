package io.akka.authelia.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.akka.authelia.application.RuleSetEntity;
import io.akka.authelia.domain.Decision;
import io.akka.authelia.domain.RuleSet;
import io.akka.authelia.domain.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Whether anything outside a test can reach this capability at all — SPEC-001 §6 — and
 * whether the trace names the rule the decision came from, which is §4 OD-1. Starts a
 * runtime.
 */
public class AuthorizationEndpointIntegrationTest extends TestKitSupport {

  private String define() {
    var id = "http-" + java.util.UUID.randomUUID();

    var setDefault =
        httpClient
            .POST("/acl/" + id + "/default")
            .withRequestBody(new AuthorizationEndpoint.DefaultTier("deny"))
            .invoke();
    assertThat(setDefault.status()).isEqualTo(StatusCodes.OK);

    append(id, "two_factor", List.of(List.of("user:alice")));
    append(id, "bypass", List.of());

    return id;
  }

  private void append(String id, String policy, List<List<String>> subjects) {
    var response =
        httpClient
            .POST("/acl/" + id + "/rules")
            .withRequestBody(
                new RuleSetEntity.RuleSpec(
                    policy,
                    List.of("app.example.com"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    subjects))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
  }

  private Decision ask(String id, String username) {
    return httpClient
        .POST("/acl/" + id + "/evaluate")
        .withRequestBody(
            new AuthorizationEndpoint.Ask(
                "https://app.example.com/", "GET", username, List.of(), "", "10.0.0.1"))
        .responseBodyAs(Decision.class)
        .invoke()
        .body();
  }

  @Test
  void theTierMovesWithTheCallerOverHttp() {
    var id = define();

    assertThat(ask(id, "").tier()).isEqualTo(Tier.TWO_FACTOR);
    assertThat(ask(id, "").provisional()).isTrue();
    assertThat(ask(id, "alice").tier()).isEqualTo(Tier.TWO_FACTOR);
    assertThat(ask(id, "bob").tier()).isEqualTo(Tier.BYPASS);
    assertThat(ask(id, "bob").provisional()).isFalse();
  }

  @Test
  void traceNamesTheRuleThatDecided() {
    var id = define();

    var trace =
        httpClient
            .POST("/acl/" + id + "/trace")
            .withRequestBody(
                new AuthorizationEndpoint.Ask(
                    "https://app.example.com/", "GET", "", List.of(), "", "10.0.0.1"))
            .responseBodyAs(RuleSet.Trace.class)
            .invoke()
            .body();

    assertThat(trace.decision().position()).isEqualTo(1);
    assertThat(trace.rows().get(0).decided()).isTrue();
    assertThat(trace.rows().get(0).matchesSubjects()).isTrue();
    assertThat(trace.rows().get(0).matchesSubjectsExactly()).isFalse();
    assertThat(trace.rows().get(1).decided()).isFalse();
    assertThat(trace.rows().get(1).reachable()).isFalse();
  }

  @Test
  void theRuleSetReadsBackInTheOrderItWasWritten() {
    var id = define();

    var set = httpClient.GET("/acl/" + id).responseBodyAs(RuleSet.class).invoke().body();

    assertThat(set.defaultTier()).isEqualTo(Tier.DENIED);
    assertThat(set.rules()).extracting("position").containsExactly(1, 2);
  }
}
