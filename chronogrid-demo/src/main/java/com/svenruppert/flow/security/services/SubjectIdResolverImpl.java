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

package com.svenruppert.flow.security.services;

import com.svenruppert.flow.security.model.AppUser;
import com.svenruppert.jsentinel.authorization.api.SubjectIdResolver;
import com.svenruppert.jsentinel.authorization.api.tenant.TenantId;
import com.svenruppert.jsentinel.logout.SubjectId;

/**
 * Maps the application's typed {@link AppUser} to the framework's
 * {@link SubjectId} key. Required for Phase-4c drift detection: the
 * {@code AppLoginView} needs to derive a stable {@code SubjectId} from
 * the just-authenticated subject to capture a
 * {@code JSentinelVersion} snapshot, and the {@code VersionBumper}
 * needs the same mapping to increment that key on role changes.
 *
 * <p>SPI-registered via
 * {@code META-INF/services/com.svenruppert.jsentinel.authorization.api.SubjectIdResolver}.
 */
public final class SubjectIdResolverImpl implements SubjectIdResolver<AppUser> {

  @Override
  public SubjectId resolve(AppUser subject) {
    return SubjectId.of(String.valueOf(subject.id()));
  }

  @Override
  public TenantId tenantFor(AppUser subject) {
    return TenantId.DEFAULT;
  }
}
