package io.akka.authelia.domain;

import static io.akka.authelia.domain.Fixtures.get;
import static io.akka.authelia.domain.Fixtures.rule;
import static io.akka.authelia.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 3, 4, 5, 6, 11, 12, 13 — question-log rows A3, A4, A5, A11, A12, A13, A14, A18. */
public class RuleCriteriaTest {

  private static final Caller CARL = user("carl", "10.0.0.1");

  private static Tier decide(Rule rule, Caller caller, Target target) {
    return new RuleSet(Tier.DENIED, List.of(rule)).evaluate(caller, target).tier();
  }

  @Test
  void everyCriterionMustMatch() {
    var r =
        rule(1, Tier.BYPASS)
            .domains("e.example.com")
            .resources("^/api/")
            .methods("get", "Post")
            .networks("10.0.0.0/24")
            .build();

    assertThat(decide(r, CARL, Target.of("https://e.example.com/api/x", "POST")))
        .isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, Target.of("https://e.example.com/api/x", "DELETE")))
        .isEqualTo(Tier.DENIED);
    assertThat(decide(r, CARL, Target.of("https://e.example.com/other", "POST")))
        .isEqualTo(Tier.DENIED);
    assertThat(decide(r, CARL, Target.of("https://f.example.com/api/x", "POST")))
        .isEqualTo(Tier.DENIED);
    assertThat(decide(r, user("carl", "192.168.2.7"), Target.of("https://e.example.com/api/x", "POST")))
        .isEqualTo(Tier.DENIED);
  }

  @Test
  void entriesWithinACriterionAreAlternatives() {
    var r = rule(1, Tier.BYPASS).domains("c.example.com", "d.example.com").build();

    assertThat(decide(r, CARL, get("https://c.example.com/"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, get("https://d.example.com/"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, get("https://e.example.com/"))).isEqualTo(Tier.DENIED);
  }

  @Test
  void entriesWithinAGroupMustAllMatch() {
    var r =
        rule(1, Tier.BYPASS)
            .domains("q.example.com")
            .query(
                List.of(
                    List.of(new QueryTerm("equal", "a", "1"), new QueryTerm("present", "b", null)),
                    List.of(new QueryTerm("pattern", "c", "^z+$"))))
            .build();

    assertThat(decide(r, CARL, get("https://q.example.com/?a=1&b="))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, get("https://q.example.com/?a=1"))).isEqualTo(Tier.DENIED);
    assertThat(decide(r, CARL, get("https://q.example.com/?c=zzz"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, get("https://q.example.com/?c=y"))).isEqualTo(Tier.DENIED);
  }

  @Test
  void everyQueryOperatorAndItsNegation() {
    var target = get("https://q.example.com/?a=1&z=");

    assertThat(QueryTerm.of("equal", "a", "1").matches(target)).isTrue();
    assertThat(QueryTerm.of("equal", "a", "2").matches(target)).isFalse();
    assertThat(QueryTerm.of("not equal", "a", "2").matches(target)).isTrue();
    assertThat(QueryTerm.of("not equal", "a", "1").matches(target)).isFalse();
    assertThat(QueryTerm.of("present", "z", null).matches(target)).isTrue();
    assertThat(QueryTerm.of("present", "q", null).matches(target)).isFalse();
    assertThat(QueryTerm.of("absent", "q", null).matches(target)).isTrue();
    assertThat(QueryTerm.of("absent", "z", null).matches(target)).isFalse();
    assertThat(QueryTerm.of("pattern", "a", "^[0-9]$").matches(target)).isTrue();
    assertThat(QueryTerm.of("not pattern", "a", "^[a-z]$").matches(target)).isTrue();
  }

  @Test
  void anEmptyCriterionMatchesEverything() {
    var r = rule(1, Tier.BYPASS).domains("app.example.com").build();

    assertThat(decide(r, CARL, Target.of("https://app.example.com/anything?x=1", "PATCH")))
        .isEqualTo(Tier.BYPASS);
    assertThat(decide(r, user("zed", "203.0.113.9"), get("https://app.example.com/")))
        .isEqualTo(Tier.BYPASS);
  }

  @Test
  void domainComparisonIsCaseInsensitive() {
    var r = rule(1, Tier.DENIED).domains("EXACT.example.com").build();

    assertThat(new RuleSet(Tier.BYPASS, List.of(r)).evaluate(CARL, get("https://EXACT.EXAMPLE.COM/")).tier())
        .isEqualTo(Tier.DENIED);
  }

  @Test
  void ruleMethodsAreUpperCasedWhenStored() {
    var r = rule(1, Tier.BYPASS).domains("e.example.com").methods("get", "Post").build();

    assertThat(r.methods()).containsExactly("GET", "POST");
    assertThat(decide(r, CARL, Target.of("https://e.example.com/", "POST"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, Target.of("https://e.example.com/", "post"))).isEqualTo(Tier.DENIED);
  }

  @Test
  void resourcePatternsSeeTheCleanedPath() {
    var r = rule(1, Tier.ONE_FACTOR).domains("e.example.com").resources("^/api/").build();

    assertThat(decide(r, CARL, get("https://e.example.com/api/../api/y"))).isEqualTo(Tier.ONE_FACTOR);
    assertThat(decide(r, CARL, get("https://e.example.com/dir/../other"))).isEqualTo(Tier.DENIED);
  }

  @Test
  void resourcePatternsSeeTheQueryString() {
    var withQuery = rule(1, Tier.BYPASS).domains("p.example.com").resources("^/api/x[?]k=v$").build();
    var trailing = rule(1, Tier.ONE_FACTOR).domains("p.example.com").resources("^/dir/$").build();

    assertThat(decide(withQuery, CARL, get("https://p.example.com/api/x?k=v"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(withQuery, CARL, get("https://p.example.com/api/x"))).isEqualTo(Tier.DENIED);
    assertThat(decide(trailing, CARL, get("https://p.example.com/dir/"))).isEqualTo(Tier.ONE_FACTOR);
    assertThat(decide(trailing, CARL, get("https://p.example.com/dir/sub/../"))).isEqualTo(Tier.ONE_FACTOR);
  }

  @Test
  void networksAreMatchedAsCidrBlocks() {
    var r = rule(1, Tier.BYPASS).domains("f.example.com").networks("192.168.1.0/24").build();

    assertThat(decide(r, user("c", "192.168.1.7"), get("https://f.example.com/"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, user("c", "192.168.2.7"), get("https://f.example.com/"))).isEqualTo(Tier.DENIED);
  }

  @Test
  void aDomainPatternIsAnAlternativeToADomainLiteral() {
    var r = rule(1, Tier.BYPASS).domainPatterns("^srv[0-9]+\\.example\\.com$").build();

    assertThat(decide(r, CARL, get("https://srv12.example.com/"))).isEqualTo(Tier.BYPASS);
    assertThat(decide(r, CARL, get("https://srvx.example.com/"))).isEqualTo(Tier.DENIED);
  }
}
