package io.akka.authelia.domain;

import java.util.List;

/**
 * Who is asking. Nothing here is verified — this capability decides what a caller would
 * have to prove, not whether they have proved it (SPEC-001 §1).
 *
 * <p>An address alone does not identify anybody, which is why {@code ip} is left out of
 * {@link #isAnonymous()} (SPEC-001 §2, question-log row A15).
 *
 * <p>Group membership is asked two ways because the source answers it two ways: a
 * {@code group:} subject entry compares exactly, while a {@code {group}} domain wildcard
 * and a {@code Group} capture group ignore case (question-log rows A10, A19).
 */
public record Caller(String username, List<String> groups, String clientId, String ip) {

  public Caller {
    username = username == null ? "" : username;
    clientId = clientId == null ? "" : clientId;
    groups = groups == null ? List.of() : List.copyOf(groups);
  }

  public boolean isAnonymous() {
    return username.isEmpty() && groups.isEmpty() && clientId.isEmpty();
  }

  public boolean hasGroup(String name) {
    return groups.contains(name);
  }

  public boolean hasGroupIgnoringCase(String name) {
    for (var i = 0; i < groups.size(); i++) {
      if (groups.get(i).equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }
}
