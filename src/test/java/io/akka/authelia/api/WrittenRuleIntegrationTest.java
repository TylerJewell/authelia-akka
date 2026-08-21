package io.akka.authelia.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import io.akka.authelia.application.RuleSetEntity;
import io.akka.authelia.domain.Decision;
import io.akka.authelia.domain.QueryTerm;
import io.akka.authelia.domain.RuleSet;
import io.akka.authelia.domain.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A rule written the way a caller writes one — as text over the interface — and then read
 * back and used. The domain tests build rules in code, which exercises a different route
 * into the same record: a criterion that a rule works out for itself is worked out on the
 * code route whether or not it survives the written one.
 *
 * <p>Starts a runtime.
 */
public class WrittenRuleIntegrationTest extends TestKitSupport {

  private String append(RuleSetEntity.RuleSpec spec) {
    var id = "written-" + java.util.UUID.randomUUID();
    assertThat(
            httpClient
                .POST("/acl/" + id + "/default")
                .withRequestBody(new AuthorizationEndpoint.DefaultTier("deny"))
                .invoke()
                .status())
        .isEqualTo(StatusCodes.OK);
    assertThat(httpClient.POST("/acl/" + id + "/rules").withRequestBody(spec).invoke().status())
        .isEqualTo(StatusCodes.OK);
    return id;
  }

  private Decision ask(String id, String url, String username) {
    return httpClient
        .POST("/acl/" + id + "/evaluate")
        .withRequestBody(
            new AuthorizationEndpoint.Ask(url, "GET", username, List.of(), "", "10.0.0.1"))
        .responseBodyAs(Decision.class)
        .invoke()
        .body();
  }

  @Test
  void aQueryPatternWrittenAsTextIsAcceptedAndMatches() {
    var id =
        append(
            new RuleSetEntity.RuleSpec(
                "bypass",
                List.of("q.example.com"),
                List.of(),
                List.of(),
                List.of(List.of(new QueryTerm("pattern", "k", "^z+$"))),
                List.of(),
                List.of(),
                List.of()));

    assertThat(ask(id, "https://q.example.com/?k=zzz", "bob").tier()).isEqualTo(Tier.BYPASS);
    assertThat(ask(id, "https://q.example.com/?k=y", "bob").tier()).isEqualTo(Tier.DENIED);
  }

  @Test
  void aCapturePatternWrittenAsTextStillMakesTheAnswerProvisional() {
    var id =
        append(
            new RuleSetEntity.RuleSpec(
                "bypass",
                List.of(),
                List.of("^(?<User>[a-z]+)\\.example\\.com$"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

    var alice = ask(id, "https://alice.example.com/", "alice");
    assertThat(alice.tier()).isEqualTo(Tier.BYPASS);
    assertThat(alice.provisional()).isTrue();
    assertThat(ask(id, "https://alice.example.com/", "bob").tier()).isEqualTo(Tier.DENIED);
  }

  @Test
  void aWrittenRuleReadsBackCarryingEveryCriterionItWasGiven() {
    var id =
        append(
            new RuleSetEntity.RuleSpec(
                "one_factor",
                List.of("W.example.com"),
                List.of("^srv[0-9]+$"),
                List.of("^/api/"),
                List.of(List.of(new QueryTerm("equal", "a", "1"))),
                List.of("get"),
                List.of("10.0.0.0/8"),
                List.of(List.of("group:ops"))));

    var set = httpClient.GET("/acl/" + id).responseBodyAs(RuleSet.class).invoke().body();
    var rule = set.rules().get(0);

    assertThat(rule.position()).isEqualTo(1);
    assertThat(rule.tier()).isEqualTo(Tier.ONE_FACTOR);
    assertThat(rule.domains()).containsExactly("w.example.com");
    assertThat(rule.methods()).containsExactly("GET");
    assertThat(rule.networks()).containsExactly("10.0.0.0/8");
    assertThat(rule.subjects()).containsExactly(List.of("group:ops"));
    assertThat(rule.domainPatterns()).singleElement().extracting(Object::toString).isEqualTo("^srv[0-9]+$");
    assertThat(rule.resources()).singleElement().extracting(Object::toString).isEqualTo("^/api/");
    assertThat(rule.query().get(0).get(0).operator()).isEqualTo("equal");
    assertThat(rule.hasSubjects()).isTrue();
  }

  @Test
  void aQueryPatternThatWillNotCompileIsRefusedRatherThanStored() {
    var id = "bad-" + java.util.UUID.randomUUID();
    httpClient
        .POST("/acl/" + id + "/default")
        .withRequestBody(new AuthorizationEndpoint.DefaultTier("deny"))
        .invoke();

    var response =
        httpClient
            .POST("/acl/" + id + "/rules")
            .withRequestBody(
                new RuleSetEntity.RuleSpec(
                    "bypass",
                    List.of("q.example.com"),
                    List.of(),
                    List.of(),
                    List.of(List.of(new QueryTerm("pattern", "k", "*"))),
                    List.of(),
                    List.of(),
                    List.of()))
            .invoke();

    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);
    assertThat(httpClient.GET("/acl/" + id).responseBodyAs(RuleSet.class).invoke().body().rules())
        .isEmpty();
  }
}
