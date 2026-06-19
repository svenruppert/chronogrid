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

import com.svenruppert.jsentinel.dx.vaadin.bootstrap.VaadinJSentinelBootstrap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Aggregates every registered {@link BootstrapExtension} via
 * {@link ServiceLoader}, sorts by {@link BootstrapExtension#order()},
 * and applies them inside a single {@code .audit(...)} /
 * {@code .sessions(...)} / {@code .credentials(...)} call on the
 * fluent {@code VaadinSecurity.bootstrap()} chain.
 *
 * <p>This is the seam that keeps higher layers additive: any number
 * of skills can each ship one extension; none of them touches
 * {@code JSentinelBootstrapInitListener}.
 */
public final class BootstrapBuilder {

  private BootstrapBuilder() {
  }

  /**
   * Applies every registered {@link BootstrapExtension} to the
   * supplied builder. The single {@code .audit / .sessions / .credentials}
   * calls let multiple contributors stack their sub-config on the
   * same builder without resetting each other.
   *
   * @param builder a fresh fluent bootstrap chain already configured
   *                with {@code authentication / authorization /
   *                loginRoute} etc.
   * @return the same builder, for chaining {@code .install()}
   */
  public static VaadinJSentinelBootstrap apply(VaadinJSentinelBootstrap builder) {
    List<BootstrapExtension> extensions = new ArrayList<>();
    ServiceLoader.load(BootstrapExtension.class).forEach(extensions::add);
    extensions.sort(Comparator.comparingInt(BootstrapExtension::order));
    return builder
        .audit(a -> extensions.forEach(e -> e.contributeAudit(a)))
        .sessions(s -> extensions.forEach(e -> e.contributeSessions(s)))
        .credentials(c -> extensions.forEach(e -> e.contributeCredentials(c)));
  }
}
