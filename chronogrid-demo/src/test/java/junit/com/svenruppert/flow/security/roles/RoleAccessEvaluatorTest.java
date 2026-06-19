/*
 * Copyright © 2013 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package junit.com.svenruppert.flow.security.roles;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.flow.security.roles.RoleAccessEvaluator;
import com.svenruppert.flow.security.roles.VisibleFor;
import com.svenruppert.flow.views.AppLoginView;
import com.svenruppert.flow.views.PublicHomeView;
import com.svenruppert.jsentinel.authorization.api.SubjectStore;
import com.svenruppert.jsentinel.authorization.api.SubjectStores;
import com.svenruppert.jsentinel.authorization.navigation.AccessContext;
import com.svenruppert.jsentinel.authorization.navigation.AccessDecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RoleAccessEvaluator — @VisibleFor → Vaadin AccessDecision")
class RoleAccessEvaluatorTest {

  private final RoleAccessEvaluator evaluator = new RoleAccessEvaluator();
  private SubjectStore previousStore;
  private FakeSubjectStore fake;

  @BeforeEach
  void setUp() {
    fake = new FakeSubjectStore();
    previousStore = SubjectStores.findSubjectStore().orElse(null);
    SubjectStores.setSubjectStore(fake);
  }

  @AfterEach
  void tearDown() {
    SubjectStores.setSubjectStore(previousStore);
  }

  // ── empty-required: must be granted ────────────────────────────

  @Test
  @DisplayName("an empty @VisibleFor value array → granted (unconditional pass)")
  void emptyRequiredRolesGranted() {
    AccessDecision d = evaluator.evaluate(
        new AccessContext("/x", PublicHomeView.class, Map.of()),
        annotationWith(/* no roles */));
    assertSame(AccessDecision.granted().getClass(), d.getClass());
  }

  // ── anonymous: denied → login ──────────────────────────────────

  @Test
  @DisplayName("no current subject → denied, reroute to /login")
  void anonymousRoutedToLogin() {
    fake.subject = Optional.empty();

    AccessDecision d = evaluator.evaluate(
        new AccessContext("/dashboard", PublicHomeView.class, Map.of()),
        annotationWith(AuthorizationRole.USER));

    String path = describe(d);
    assertTrue(path.contains(AppLoginView.NAV),
        "expected denial target to mention the login route, got: " + path);
  }

  // ── insufficient role: denied → public home ───────────────────

  @Test
  @DisplayName("subject without the required role → denied, reroute to /")
  void insufficientRoleRoutedHome() {
    fake.subject = Optional.of(new AppUser(7L, "user-only",
        EnumSet.of(AuthorizationRole.USER)));

    AccessDecision d = evaluator.evaluate(
        new AccessContext("/audit", PublicHomeView.class, Map.of()),
        annotationWith(AuthorizationRole.ADMIN));

    String path = describe(d);
    assertFalse(path.contains(AppLoginView.NAV),
        "insufficient-role denial must NOT reroute to login: " + path);
  }

  // ── sufficient role: granted ───────────────────────────────────

  @Test
  @DisplayName("subject with a required role → granted")
  void sufficientRoleGranted() {
    fake.subject = Optional.of(new AppUser(8L, "admin",
        EnumSet.of(AuthorizationRole.ADMIN, AuthorizationRole.USER)));

    AccessDecision d = evaluator.evaluate(
        new AccessContext("/audit", PublicHomeView.class, Map.of()),
        annotationWith(AuthorizationRole.ADMIN));

    assertSame(AccessDecision.granted().getClass(), d.getClass());
  }

  @Test
  @DisplayName("any-of semantics: subject with one of several required roles → granted")
  void anyOfRolesGranted() {
    fake.subject = Optional.of(new AppUser(9L, "user",
        EnumSet.of(AuthorizationRole.USER)));

    AccessDecision d = evaluator.evaluate(
        new AccessContext("/x", PublicHomeView.class, Map.of()),
        annotationWith(AuthorizationRole.ADMIN, AuthorizationRole.USER));

    assertSame(AccessDecision.granted().getClass(), d.getClass());
  }

  // ── helpers ────────────────────────────────────────────────────

  /** Builds a synthetic {@link VisibleFor} annotation for the test. */
  private static VisibleFor annotationWith(AuthorizationRole... roles) {
    return new VisibleFor() {
      @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return VisibleFor.class;
      }
      @Override public AuthorizationRole[] value() { return roles; }
    };
  }

  private static String describe(AccessDecision d) {
    // AccessDecision is sealed; toString() includes the route literal for
    // reroute/denied variants and is stable enough to grep against.
    return String.valueOf(d);
  }

  /** Hand-rolled SubjectStore — no Vaadin session needed. */
  private static final class FakeSubjectStore implements SubjectStore {
    Optional<AppUser> subject = Optional.empty();
    final Map<Class<?>, Object> generic = new HashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> currentSubject(Class<T> type) {
      if (type == AppUser.class) return (Optional<T>) subject;
      return Optional.ofNullable((T) generic.get(type));
    }
    @Override public <T> void setCurrentSubject(T value, Class<T> type) {
      if (type == AppUser.class) subject = Optional.of((AppUser) value);
      else generic.put(type, value);
    }
    @Override public <T> void deleteCurrentSubject(Class<T> type) {
      if (type == AppUser.class) subject = Optional.empty();
      else generic.remove(type);
    }
  }
}
