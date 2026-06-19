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

package com.svenruppert.flow.security.bootstrap;

import com.svenruppert.jsentinel.credential.password.PasswordHashingServices;
import com.svenruppert.jsentinel.dx.bootstrap.AuditBootstrap;
import com.svenruppert.jsentinel.dx.bootstrap.CredentialBootstrap;

/**
 * Layer-1 defaults: in-memory 256-entry audit ring buffer + console
 * logging, PBKDF2 for password hashing. Registered via
 * {@code META-INF/services/} + {@link BootstrapExtension#SERVICE_NAME}.
 *
 * <p>{@code order() == 0} so this runs before any persistence /
 * hardening extension — those layers override the defaults by
 * stacking their own {@code .storeBacked(...)} / {@code .hashing(...)}
 * calls on the same sub-builder.
 */
public final class DefaultBootstrapExtension implements BootstrapExtension {

  @Override
  public void contributeAudit(AuditBootstrap a) {
    a.ringBuffer(256).logging();
  }

  @Override
  public void contributeCredentials(CredentialBootstrap c) {
    c.hashing(PasswordHashingServices.defaults());
  }

  @Override
  public int order() {
    return 0;
  }
}
