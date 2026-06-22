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

package com.svenruppert.chronogrid.provider;

import java.net.URI;

/**
 * Fallback profile for CalDAV servers that don't match any of the
 * known-provider sniffers. Sits last in the {@link
 * ProviderRegistry} chain and matches everything via
 * {@link #matches(URI)} returning {@code true} so the registry
 * never has to throw.
 *
 * <p><strong>Strategy:</strong> assume a standards-compliant
 * CalDAV server. RFC-7986 {@code COLOR} is preserved as hex; no
 * DESCRIPTION marker (no point — generic providers either
 * preserve everything or strip everything; the marker is only a
 * net win when the provider preserves DESCRIPTION but strips
 * everything else, which is the specific Apple-iCloud
 * pathology).
 *
 * <p>If a generic server turns out to need a quirk, file a bug
 * and add a dedicated profile. This default is "trust the spec".
 */
public final class GenericProvider implements CalDavProviderProfile {

  @Override
  public String id() {
    return "generic";
  }

  @Override
  public boolean matches(URI uri) {
    return true;
  }

  @Override
  public String formatColor(String hex) {
    return hex;
  }

  @Override
  public boolean writeDescriptionMarker() {
    return false;
  }
}
