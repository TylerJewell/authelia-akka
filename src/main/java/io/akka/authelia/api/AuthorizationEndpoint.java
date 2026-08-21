package io.akka.authelia.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import io.akka.authelia.application.RuleSetEntity;
import io.akka.authelia.domain.Caller;
import io.akka.authelia.domain.Decision;
import io.akka.authelia.domain.RuleSet;
import java.util.List;

/**
 * The capability's own surface: define a rule set, ask it what a caller would have to
 * reach, and read back why (SPEC-001 §6).
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/acl")
public class AuthorizationEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;

  public AuthorizationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record DefaultTier(String policy) {}

  /** A request as a caller writes it, with the identity flattened out of the nested record. */
  public record Ask(
      String url, String method, String username, List<String> groups, String clientId, String ip) {

    RuleSetEntity.Request toRequest() {
      return new RuleSetEntity.Request(
          new Caller(username, groups == null ? List.of() : groups, clientId, ip == null ? "0.0.0.0" : ip),
          url,
          method == null ? "GET" : method);
    }
  }

  @Post("/{id}/default")
  public String setDefaultTier(String id, DefaultTier body) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(RuleSetEntity::setDefaultTier)
        .invoke(body.policy());
  }

  @Post("/{id}/rules")
  public Integer appendRule(String id, RuleSetEntity.RuleSpec body) {
    return componentClient.forEventSourcedEntity(id).method(RuleSetEntity::appendRule).invoke(body);
  }

  @Post("/{id}/evaluate")
  public Decision evaluate(String id, Ask body) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(RuleSetEntity::evaluate)
        .invoke(body.toRequest());
  }

  @Post("/{id}/trace")
  public RuleSet.Trace trace(String id, Ask body) {
    return componentClient
        .forEventSourcedEntity(id)
        .method(RuleSetEntity::trace)
        .invoke(body.toRequest());
  }

  @Get("/{id}")
  public RuleSet read(String id) {
    return componentClient.forEventSourcedEntity(id).method(RuleSetEntity::read).invoke();
  }
}
