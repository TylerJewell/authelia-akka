package io.akka.authelia.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.akka.authelia.domain.Caller;
import io.akka.authelia.domain.QueryTerm;
import io.akka.authelia.domain.Rule;
import io.akka.authelia.domain.RuleSet;
import io.akka.authelia.domain.Target;
import io.akka.authelia.domain.Tier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Answers a scenario file the way {@code probes/oracle} answers it, so the two can be
 * compared row for row.
 *
 * <pre>
 *   java ... Harness &lt;scenario.json&gt;            answers, as JSON, on stdout
 *   java ... Harness &lt;scenario.json&gt; --bench N  N timed repetitions, nanoseconds per call
 * </pre>
 *
 * <p>It reads the same file the Go probe reads and calls the same entry point the entity
 * calls, so what it times is the walk over the rule set and nothing around it.
 */
public final class Harness {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Harness() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("usage: Harness <scenario.json> [--bench N]");
      System.exit(2);
    }

    var scenario = MAPPER.readTree(Files.readString(Path.of(args[0])));
    var set = ruleSet(scenario);
    var requests = requests(scenario);

    if (args.length >= 3 && args[1].equals("--bench")) {
      bench(set, requests, Integer.parseInt(args[2]));
      return;
    }

    var out = MAPPER.createArrayNode();
    for (var request : requests) {
      var decision = set.evaluate(request.caller(), request.target());
      var row = out.addObject();
      row.put("name", request.name());
      row.put("level", decision.tier().policyName());
      row.put("has_subjects", decision.provisional());
    }

    System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out));
  }

  private record Ask(String name, Caller caller, Target target) {}

  private static void bench(RuleSet set, List<Ask> requests, int reps) {
    for (var ask : requests) {
      set.evaluate(ask.caller(), ask.target());
    }

    var start = System.nanoTime();
    for (var i = 0; i < reps; i++) {
      for (var ask : requests) {
        set.evaluate(ask.caller(), ask.target());
      }
    }
    var elapsed = System.nanoTime() - start;
    var calls = (long) reps * requests.size();

    System.out.printf(
        "{\"calls\": %d, \"ns_total\": %d, \"ns_per_call\": %.1f}%n",
        calls, elapsed, (double) elapsed / calls);
  }

  private static RuleSet ruleSet(JsonNode scenario) {
    var rules = new ArrayList<Rule>();
    var position = 0;

    for (var node : array(scenario, "rules")) {
      position++;
      rules.add(
          Rule.of(
              position,
              Tier.fromPolicyName(node.path("policy").asText("")),
              strings(node, "domains"),
              strings(node, "domains_regex"),
              strings(node, "resources"),
              query(node),
              strings(node, "methods"),
              strings(node, "networks"),
              subjects(node)));
    }

    return new RuleSet(Tier.fromPolicyName(scenario.path("default_policy").asText("")), rules);
  }

  private static List<Ask> requests(JsonNode scenario) {
    var asks = new ArrayList<Ask>();

    for (var node : array(scenario, "requests")) {
      var ip = node.path("ip").asText("");
      asks.add(
          new Ask(
              node.path("name").asText(""),
              new Caller(
                  node.path("username").asText(""),
                  strings(node, "groups"),
                  node.path("client_id").asText(""),
                  ip.isEmpty() ? "0.0.0.0" : ip),
              Target.of(node.path("url").asText(""), node.path("method").asText("GET"))));
    }

    return asks;
  }

  private static List<List<QueryTerm>> query(JsonNode rule) {
    var groups = new ArrayList<List<QueryTerm>>();

    for (var group : array(rule, "query")) {
      var terms = new ArrayList<QueryTerm>();
      for (var term : group) {
        terms.add(
            new QueryTerm(
                term.path("operator").asText(""),
                term.path("key").asText(""),
                term.path("value").asText("")));
      }
      groups.add(terms);
    }

    return groups;
  }

  private static List<List<String>> subjects(JsonNode rule) {
    var groups = new ArrayList<List<String>>();
    for (var group : array(rule, "subjects")) {
      var entries = new ArrayList<String>();
      group.forEach(e -> entries.add(e.asText()));
      groups.add(entries);
    }
    return groups;
  }

  private static List<String> strings(JsonNode node, String field) {
    var out = new ArrayList<String>();
    array(node, field).forEach(e -> out.add(e.asText()));
    return out;
  }

  private static Iterable<JsonNode> array(JsonNode node, String field) {
    var value = node.path(field);
    return value instanceof ArrayNode a ? a : List.of();
  }
}
