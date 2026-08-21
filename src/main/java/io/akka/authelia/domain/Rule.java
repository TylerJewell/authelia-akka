package io.akka.authelia.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One access rule: a position, the tier it grants, and eight criteria that all have to
 * match for it to apply (SPEC-001 §2, §3 rules 3–6).
 *
 * <p>The eight are the six the source has, with its two domain forms — literal and regular
 * expression — kept apart so that the shape of an entry says which one it is rather than
 * being guessed from its text. They are treated as one criterion when matching: a rule with
 * both matches a domain that satisfies either (question-log row A4).
 *
 * <p>Patterns are held compiled and {@code hasSubjects} is worked out once, both by
 * {@link #of}. Evaluation happens once per request and rules are written rarely, so
 * anything that can be settled at write time is.
 */
public record Rule(
    int position,
    Tier tier,
    List<String> domains,
    List<Pattern> domainPatterns,
    List<Pattern> resources,
    List<List<QueryTerm>> query,
    List<String> methods,
    List<String> networks,
    List<List<String>> subjects,
    boolean hasSubjects) {

  /** A criterion that cannot be understood, named without repeating what the caller wrote. */
  public static final class MalformedRule extends IllegalArgumentException {
    public MalformedRule(String message) {
      super(message);
    }
  }

  /**
   * Normalises a rule the way the source normalises one when it is loaded: domain literals
   * lower-cased, method names upper-cased, and subject entries without a recognised prefix
   * dropped (question-log rows A11, A12, and the prefix list in the source's subject parser).
   */
  public static Rule of(
      int position,
      Tier tier,
      List<String> domains,
      List<String> domainPatterns,
      List<String> resources,
      List<List<QueryTerm>> query,
      List<String> methods,
      List<String> networks,
      List<List<String>> subjects) {

    var compiledDomains = compileAll(orEmpty(domainPatterns), "domain pattern", position);
    var compiledResources = compileAll(orEmpty(resources), "resource pattern", position);
    orEmpty(query).forEach(group -> group.forEach(term -> term.validate(position)));
    orEmpty(networks).forEach(cidr -> Cidr.parse(cidr, position));

    List<List<String>> keptSubjects = new ArrayList<>();
    for (var group : orEmpty(subjects)) {
      var kept = group.stream().filter(Rule::isRecognisedSubject).toList();
      if (!kept.isEmpty()) {
        keptSubjects.add(kept);
      }
    }

    var lowerDomains = orEmpty(domains).stream().map(d -> d.toLowerCase(Locale.ROOT)).toList();

    return new Rule(
        position,
        tier == null ? Tier.DENIED : tier,
        lowerDomains,
        compiledDomains,
        compiledResources,
        orEmpty(query).stream().map(List::copyOf).toList(),
        orEmpty(methods).stream().map(m -> m.toUpperCase(Locale.ROOT)).toList(),
        List.copyOf(orEmpty(networks)),
        List.copyOf(keptSubjects),
        subjectsDependedOn(keptSubjects, lowerDomains, compiledDomains, compiledResources));
  }

  private static List<Pattern> compileAll(List<String> sources, String what, int position) {
    var compiled = new ArrayList<Pattern>(sources.size());
    for (var source : sources) {
      try {
        compiled.add(Pattern.compile(source));
      } catch (PatternSyntaxException unusable) {
        throw new MalformedRule("rule " + position + " has a " + what + " that is not a valid regular expression");
      }
    }
    return List.copyOf(compiled);
  }

  private static <T> List<T> orEmpty(List<T> in) {
    return in == null ? List.of() : in;
  }

  private static boolean isRecognisedSubject(String entry) {
    return entry.startsWith("user:") || entry.startsWith("group:") || entry.startsWith("oauth2:client:");
  }

  /**
   * Whether this rule's answer can change once the caller is identified — the flag the
   * decision carries as {@code provisional} (SPEC-001 §3 rule 10, question-log row A9).
   */
  private static boolean subjectsDependedOn(
      List<List<String>> subjects,
      List<String> domains,
      List<Pattern> domainPatterns,
      List<Pattern> resources) {
    if (!subjects.isEmpty()) {
      return true;
    }
    if (domains.stream().anyMatch(d -> d.startsWith("{user}") || d.startsWith("{group}"))) {
      return true;
    }
    return java.util.stream.Stream.concat(domainPatterns.stream(), resources.stream())
        .anyMatch(p -> Patterns.declaresSubjectGroup(p.pattern()));
  }

  public boolean matches(Caller caller, Target target) {
    return matchesDomains(caller, target)
        && matchesResources(caller, target)
        && matchesQuery(target)
        && matchesMethods(target)
        && matchesNetworks(caller)
        && matchesSubjects(caller);
  }

  public boolean matchesDomains(Caller caller, Target target) {
    if (domains.isEmpty() && domainPatterns.isEmpty()) {
      return true;
    }
    for (var i = 0; i < domains.size(); i++) {
      if (DomainCriterion.matches(domains.get(i), target.domain(), caller)) {
        return true;
      }
    }
    for (var i = 0; i < domainPatterns.size(); i++) {
      if (matchesPattern(domainPatterns.get(i), target.domain(), caller)) {
        return true;
      }
    }
    return false;
  }

  public boolean matchesResources(Caller caller, Target target) {
    if (resources.isEmpty()) {
      return true;
    }
    for (var i = 0; i < resources.size(); i++) {
      if (matchesPattern(resources.get(i), target.path(), caller)) {
        return true;
      }
    }
    return false;
  }

  public boolean matchesQuery(Target target) {
    if (query.isEmpty()) {
      return true;
    }
    for (var i = 0; i < query.size(); i++) {
      if (allTermsMatch(query.get(i), target)) {
        return true;
      }
    }
    return false;
  }

  private static boolean allTermsMatch(List<QueryTerm> group, Target target) {
    for (var i = 0; i < group.size(); i++) {
      if (!group.get(i).matches(target)) {
        return false;
      }
    }
    return true;
  }

  public boolean matchesMethods(Target target) {
    return methods.isEmpty() || methods.contains(target.method());
  }

  public boolean matchesNetworks(Caller caller) {
    if (networks.isEmpty()) {
      return true;
    }
    for (var i = 0; i < networks.size(); i++) {
      if (Cidr.contains(networks.get(i), caller.ip())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The rule this port exists to describe: a caller with no identity matches whatever the
   * subject criterion names, so the walk stops here and the tier it grants is reported as
   * provisional (SPEC-001 §3 rule 8, question-log row A7).
   */
  public boolean matchesSubjects(Caller caller) {
    return caller.isAnonymous() || matchesSubjectsExactly(caller);
  }

  public boolean matchesSubjectsExactly(Caller caller) {
    if (subjects.isEmpty()) {
      return true;
    }
    if (caller.isAnonymous()) {
      return false;
    }
    for (var i = 0; i < subjects.size(); i++) {
      if (allSubjectsMatch(subjects.get(i), caller)) {
        return true;
      }
    }
    return false;
  }

  private static boolean allSubjectsMatch(List<String> group, Caller caller) {
    for (var i = 0; i < group.size(); i++) {
      if (!matchesSubject(group.get(i), caller)) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesSubject(String entry, Caller caller) {
    if (entry.startsWith("user:")) {
      return caller.username().equals(entry.substring("user:".length()).trim());
    }
    if (entry.startsWith("group:")) {
      return caller.hasGroup(entry.substring("group:".length()).trim());
    }
    return caller.clientId().equals(entry.substring("oauth2:client:".length()).trim());
  }

  /**
   * A pattern with a {@code User} or {@code Group} capture group is searched rather than
   * anchored, and what it captured is compared against the caller ignoring case; an
   * anonymous caller passes on the search alone (question-log row A19).
   */
  private boolean matchesPattern(Pattern pattern, String input, Caller caller) {
    var matcher = pattern.matcher(input);
    if (!matcher.find()) {
      return false;
    }
    // No rule with a capture group can reach here with hasSubjects false, and a pattern
    // without one captures nothing below — so the flag decides for both cases at once.
    if (caller.isAnonymous() || !hasSubjects) {
      return true;
    }

    var user = Patterns.captured(matcher, "User");
    if (user != null && !user.equalsIgnoreCase(caller.username())) {
      return false;
    }

    var group = Patterns.captured(matcher, "Group");
    return group == null || caller.hasGroupIgnoringCase(group);
  }
}
