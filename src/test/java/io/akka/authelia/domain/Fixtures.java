package io.akka.authelia.domain;

import java.util.List;

/**
 * Short ways to say "a rule with only these criteria set". A rule has eight criteria and
 * a test normally varies one, so spelling out the other seven at every call site would
 * bury what each test is actually about.
 */
final class Fixtures {

  private Fixtures() {}

  static final class RuleBuilder {
    private final int position;
    private final Tier tier;
    private List<String> domains = List.of();
    private List<String> domainPatterns = List.of();
    private List<String> resources = List.of();
    private List<List<QueryTerm>> query = List.of();
    private List<String> methods = List.of();
    private List<String> networks = List.of();
    private List<List<String>> subjects = List.of();

    RuleBuilder(int position, Tier tier) {
      this.position = position;
      this.tier = tier;
    }

    RuleBuilder domains(String... v) {
      domains = List.of(v);
      return this;
    }

    RuleBuilder domainPatterns(String... v) {
      domainPatterns = List.of(v);
      return this;
    }

    RuleBuilder resources(String... v) {
      resources = List.of(v);
      return this;
    }

    RuleBuilder query(List<List<QueryTerm>> v) {
      query = v;
      return this;
    }

    RuleBuilder methods(String... v) {
      methods = List.of(v);
      return this;
    }

    RuleBuilder networks(String... v) {
      networks = List.of(v);
      return this;
    }

    RuleBuilder subjects(List<List<String>> v) {
      subjects = v;
      return this;
    }

    Rule build() {
      return Rule.of(
          position, tier, domains, domainPatterns, resources, query, methods, networks, subjects);
    }
  }

  static RuleBuilder rule(int position, Tier tier) {
    return new RuleBuilder(position, tier);
  }

  static Caller anonymous(String ip) {
    return new Caller("", List.of(), "", ip);
  }

  static Caller user(String username, String ip, String... groups) {
    return new Caller(username, List.of(groups), "", ip);
  }

  static Target get(String url) {
    return Target.of(url, "GET");
  }
}
