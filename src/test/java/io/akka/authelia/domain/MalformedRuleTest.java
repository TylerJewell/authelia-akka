package io.akka.authelia.domain;

import static io.akka.authelia.domain.Fixtures.rule;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A criterion that cannot be understood is refused when the rule is written, not thrown
 * from the middle of somebody's authorization question. The refusal names the criterion and
 * the rule's position, and does not repeat what the caller wrote.
 */
public class MalformedRuleTest {

  @Test
  void anUnusableDomainPatternIsRefused() {
    assertThatThrownBy(() -> rule(3, Tier.BYPASS).domainPatterns("^(unclosed").build())
        .isInstanceOf(Rule.MalformedRule.class)
        .hasMessageContaining("rule 3")
        .hasMessageContaining("domain pattern")
        .hasMessageNotContaining("unclosed");
  }

  @Test
  void anUnusableResourcePatternIsRefused() {
    assertThatThrownBy(() -> rule(1, Tier.BYPASS).resources("[").build())
        .isInstanceOf(Rule.MalformedRule.class)
        .hasMessageContaining("resource pattern");
  }

  @Test
  void anUnusableQueryPatternIsRefused() {
    assertThatThrownBy(
            () ->
                rule(2, Tier.BYPASS)
                    .query(List.of(List.of(new QueryTerm("pattern", "k", "*"))))
                    .build())
        .isInstanceOf(Rule.MalformedRule.class)
        .hasMessageContaining("query pattern");
  }

  @Test
  void aQueryTermThatTakesNoPatternIsUnaffectedByItsValue() {
    assertThatCode(
            () ->
                rule(2, Tier.BYPASS)
                    .query(List.of(List.of(new QueryTerm("equal", "k", "*"))))
                    .build())
        .doesNotThrowAnyException();
  }

  @Test
  void anUnusableNetworkIsRefused() {
    assertThatThrownBy(() -> rule(4, Tier.BYPASS).networks("not-a-network").build())
        .isInstanceOf(Rule.MalformedRule.class)
        .hasMessageContaining("network");
    assertThatThrownBy(() -> rule(4, Tier.BYPASS).networks("10.0.0.0/99").build())
        .isInstanceOf(Rule.MalformedRule.class);
  }

  @Test
  void aNetworkCriterionNeverResolvesANameToAnAddress() {
    // A name in a network would put a lookup inside an authorization decision. It is
    // refused as unreadable rather than resolved.
    assertThatThrownBy(() -> rule(1, Tier.BYPASS).networks("localhost/32").build())
        .isInstanceOf(Rule.MalformedRule.class);
  }
}
