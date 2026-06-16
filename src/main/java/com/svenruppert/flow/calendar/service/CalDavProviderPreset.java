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

package com.svenruppert.flow.calendar.service;

import com.vaadin.flow.component.icon.VaadinIcon;

import java.util.List;
import java.util.Objects;

/**
 * One-click backend preset shown as an icon button in the Settings
 * dialog. Picks an {@code entryUri} (the URL the user should enter
 * before running discovery) and ships a provider-specific hint text
 * that replaces the generic one when the preset is selected.
 *
 * <p>{@code id} doubles as a stable test selector (e.g. "icloud")
 * and as the i18n key suffix
 * ({@code calendar.settings.provider.<id>.label} /
 * {@code …hint}).
 *
 * <p>The static {@link #DEFAULTS} list is what the UI iterates over;
 * future providers (Nextcloud, Radicale, Baïkal, mailbox.org, …)
 * each ship one entry here — no other code change needed.
 */
public record CalDavProviderPreset(String id,
                                   String label,
                                   VaadinIcon icon,
                                   String entryUri,
                                   String hint) {

  public CalDavProviderPreset {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(entryUri, "entryUri");
    Objects.requireNonNull(hint, "hint");
  }

  public static final CalDavProviderPreset ICLOUD = new CalDavProviderPreset(
      "icloud",
      "Apple iCloud",
      VaadinIcon.CLOUD,
      "https://caldav.icloud.com/",
      "Apple iCloud — your username is the Apple ID e-mail address; "
          + "the password MUST be an app-specific password generated at "
          + "appleid.apple.com → Sign-In and Security → App-Specific "
          + "Passwords. Your regular Apple ID password will NOT work — "
          + "Apple's mandatory two-factor authentication blocks it for "
          + "any non-Apple client. After entering the credentials, hit "
          + "Discover calendars to pick your calendar.");

  public static final List<CalDavProviderPreset> DEFAULTS = List.of(ICLOUD);
}
