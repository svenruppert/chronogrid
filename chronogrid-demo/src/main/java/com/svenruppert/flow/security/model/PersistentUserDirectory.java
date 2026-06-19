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

package com.svenruppert.flow.security.model;

import com.svenruppert.dependencies.core.logger.HasLogger;
import com.svenruppert.flow.security.roles.AuthorizationRole;
import com.svenruppert.jsentinel.audit.JSentinelAuditService;
import com.svenruppert.jsentinel.audit.RoleAssigned;
import com.svenruppert.jsentinel.audit.RoleRevoked;
import com.svenruppert.jsentinel.audit.UserCreated;
import com.svenruppert.jsentinel.audit.UserDeleted;
import com.svenruppert.jsentinel.authorization.api.JSentinelServiceResolver;
import com.svenruppert.jsentinel.credential.password.CredentialVerificationResult;
import com.svenruppert.jsentinel.credential.password.PasswordHashingService;
import com.svenruppert.jsentinel.credential.password.RehashDecision;
import com.svenruppert.jsentinel.credential.password.bouncycastle.BouncyCastleHashingServices;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * {@link UserDirectory} backed by a pluggable
 * {@link UserDirectoryPersistence}.
 *
 * <p>The persistence layer is the only seam that touches durable
 * state — this class keeps in-memory views ({@link #byUsername},
 * {@link #byId}) for fast reads and delegates load / save to the
 * injected implementation.
 *
 * <p>Default wiring (no-arg constructor):
 * {@link EclipseStoreUserDirectoryPersistence} + Argon2id hashing via
 * {@link BouncyCastleHashingServices#modern()}. Tests can swap in
 * {@link InMemoryUserDirectoryPersistence} via the two-arg constructor.
 *
 * <p>Hashing goes through the same {@link PasswordHashingService} the
 * hardening layer registers on the runtime, so the wire format
 * matches what the bootstrap pipeline produces — an admin created
 * via {@code /setup} can sign in.
 */
public final class PersistentUserDirectory implements UserDirectory, HasLogger {

  private final UserDirectoryPersistence persistence;
  private final PasswordHashingService hashingService;
  private final Map<String, StoredUser> byUsername = new ConcurrentHashMap<>();
  private final Map<Long, AppUser> byId = new ConcurrentHashMap<>();

  public PersistentUserDirectory(UserDirectoryPersistence persistence,
                                 PasswordHashingService hashingService) {
    this.persistence = Objects.requireNonNull(persistence, "persistence");
    this.hashingService = Objects.requireNonNull(hashingService, "hashingService");
    Map<String, StoredUser> initial = persistence.load();
    byUsername.putAll(initial);
    for (StoredUser stored : initial.values()) {
      byId.put(stored.user().id(), stored.user());
    }
    logger().info("PersistentUserDirectory loaded: {} users from {}",
        byUsername.size(), persistence.getClass().getSimpleName());
  }

  public PersistentUserDirectory() {
    this(new EclipseStoreUserDirectoryPersistence(),
        BouncyCastleHashingServices.modern());
  }

  // ── UserDirectory ──────────────────────────────────────────────

  @Override
  public Optional<AppUser> findByCredentials(Credentials credentials) {
    if (credentials == null
        || credentials.username() == null
        || credentials.password() == null) {
      return Optional.empty();
    }
    StoredUser stored = byUsername.get(credentials.username());
    if (stored == null) return Optional.empty();
    char[] raw = credentials.password().toCharArray();
    CredentialVerificationResult verification =
        hashingService.verify(raw, stored.passwordHash());
    if (!(verification instanceof CredentialVerificationResult.Verified)) {
      return Optional.empty();
    }
    if (hashingService.needsRehash(stored.passwordHash()) instanceof RehashDecision.Required) {
      try {
        String fresh = hashingService.hash(raw).encodedHash();
        byUsername.put(credentials.username(), new StoredUser(stored.user(), fresh));
        persist();
        logger().debug("Transparent rehash for user '{}'", credentials.username());
      } catch (RuntimeException ignored) {
        // login already succeeded; rehash failure is not a security failure
      }
    }
    return Optional.of(stored.user());
  }

  @Override
  public Optional<AppUser> findById(Long id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Stream<AppUser> all() {
    return byUsername.values().stream().map(StoredUser::user);
  }

  @Override
  public boolean hasAnyAdministrator() {
    return byUsername.values().stream()
        .anyMatch(stored -> stored.user().roles().contains(AuthorizationRole.ADMIN));
  }

  @Override
  public synchronized void addUser(String username, String plaintextPassword, AppUser user) {
    Objects.requireNonNull(username);
    Objects.requireNonNull(plaintextPassword);
    Objects.requireNonNull(user);
    String hash = hashingService.hash(plaintextPassword.toCharArray()).encodedHash();
    byUsername.put(username, new StoredUser(user, hash));
    byId.put(user.id(), user);
    persist();
    audit(new UserCreated(Instant.now(Clock.systemUTC()), username, firstRoleOf(user), null));
  }

  @Override
  public synchronized void registerWithHashedPassword(String username, String passwordHash, AppUser user) {
    if (byUsername.containsKey(username)) {
      throw new IllegalStateException("user already exists: " + username);
    }
    byUsername.put(username, new StoredUser(user, passwordHash));
    byId.put(user.id(), user);
    persist();
  }

  @Override
  public synchronized void deleteUser(Long id) {
    AppUser removed = byId.remove(id);
    if (removed == null) return;
    String username = byUsername.entrySet().stream()
        .filter(e -> e.getValue().user().equals(removed))
        .map(Map.Entry::getKey).findFirst().orElse(null);
    byUsername.values().removeIf(stored -> stored.user().equals(removed));
    persist();
    if (username != null) {
      audit(new UserDeleted(Instant.now(Clock.systemUTC()), username, null));
    }
  }

  @Override
  public synchronized void assignRole(Long id, AuthorizationRole role) {
    Objects.requireNonNull(role, "role");
    if (id == null) return;
    AppUser current = byId.get(id);
    if (current == null || current.roles().contains(role)) return;
    EnumSet<AuthorizationRole> next = roleSetOf(current);
    next.add(role);
    replace(current, new AppUser(current.id(), current.name(), next));
    persist();
    audit(new RoleAssigned(Instant.now(Clock.systemUTC()),
        current.id().toString(), role.name(), null));
  }

  @Override
  public synchronized void revokeRole(Long id, AuthorizationRole role) {
    Objects.requireNonNull(role, "role");
    if (id == null) return;
    AppUser current = byId.get(id);
    if (current == null || !current.roles().contains(role)) return;
    EnumSet<AuthorizationRole> next = roleSetOf(current);
    next.remove(role);
    replace(current, new AppUser(current.id(), current.name(), next));
    persist();
    audit(new RoleRevoked(Instant.now(Clock.systemUTC()),
        current.id().toString(), role.name(), null));
  }

  // ── Internal ───────────────────────────────────────────────────

  private void persist() {
    try {
      persistence.save(new HashMap<>(byUsername));
    } catch (RuntimeException failure) {
      logger().error("Failed to persist user directory ({} entries) via {}",
          byUsername.size(), persistence.getClass().getSimpleName(), failure);
      throw failure;
    }
  }

  private void replace(AppUser oldUser, AppUser newUser) {
    byId.put(newUser.id(), newUser);
    byUsername.replaceAll((username, stored) ->
        stored.user().equals(oldUser) ? new StoredUser(newUser, stored.passwordHash()) : stored);
  }

  private static EnumSet<AuthorizationRole> roleSetOf(AppUser user) {
    EnumSet<AuthorizationRole> set = EnumSet.noneOf(AuthorizationRole.class);
    set.addAll(user.roles());
    return set;
  }

  private static String firstRoleOf(AppUser user) {
    if (user.roles().contains(AuthorizationRole.ADMIN)) return AuthorizationRole.ADMIN.name();
    if (user.roles().contains(AuthorizationRole.USER)) return AuthorizationRole.USER.name();
    return "USER";
  }

  private static void audit(com.svenruppert.jsentinel.audit.AuditEvent event) {
    try {
      JSentinelAuditService sink = JSentinelServiceResolver.securityAuditService();
      sink.publish(event);
    } catch (RuntimeException ignored) {
      // audit must never block user-management calls
    }
  }
}
