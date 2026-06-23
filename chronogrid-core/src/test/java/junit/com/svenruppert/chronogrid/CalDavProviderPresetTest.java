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

package junit.com.svenruppert.chronogrid;

/*-
 * #%L
 * Calendar — CalDAV headless
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2013 - 2026 Sven Ruppert
 * %%
 * Licensed under the EUPL, Version 1.1 or – as soon they will be
 * approved by the European Commission - subsequent versions of the
 * EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl5
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 * #L%
 */

import com.svenruppert.chronogrid.service.CalDavProviderPreset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CalDavProviderPreset — DEFAULTS catalogue + iCloud + Google entries")
class CalDavProviderPresetTest {

  @Test
  @DisplayName("DEFAULTS contains iCloud (index 0) + Google (index 1) presets")
  void defaultsContainIcloudThenGoogle() {
    assertEquals(2, CalDavProviderPreset.DEFAULTS.size(),
        "this iteration ships exactly two presets; add a row when more land");
    assertEquals(CalDavProviderPreset.ICLOUD,
        CalDavProviderPreset.DEFAULTS.get(0),
        "iCloud must stay at index 0 — companion-blog default per "
            + "memory project_blog_focus_icloud");
    assertEquals(CalDavProviderPreset.GOOGLE,
        CalDavProviderPreset.DEFAULTS.get(1));
  }

  @Test
  @DisplayName("Planning-Feature #9: GOOGLE preset uses the canonical CalDAV-v2 base URL")
  void googleUsesCanonicalCalDavBaseUrl() {
    assertEquals("https://apidata.googleusercontent.com/caldav/v2/",
        CalDavProviderPreset.GOOGLE.entryUri());
    assertEquals("google", CalDavProviderPreset.GOOGLE.id());
    assertEquals("Google Calendar", CalDavProviderPreset.GOOGLE.label());
  }

  @Test
  @DisplayName("Planning-Feature #9: GOOGLE hint mentions OAuth + VTODO + COLOR constraints")
  void googleHintMentionsConstraints() {
    String hint = CalDavProviderPreset.GOOGLE.hint();
    assertTrue(hint.contains("OAuth"),
        "Google hint must surface that it's OAuth 2.0 — sets the user's "
            + "expectation that the credentials step looks different");
    assertTrue(hint.contains("VTODO"),
        "Google hint must call out the VTODO limitation so users don't "
            + "lose tasks silently");
    assertTrue(hint.contains("COLOR"),
        "Google hint must explain why per-event colour does not show "
            + "in Google's own UI");
  }

  @Test
  @DisplayName("ICLOUD preset uses the canonical discovery entry URL")
  void icloudUsesCanonicalEntryUrl() {
    assertEquals("https://caldav.icloud.com/",
        CalDavProviderPreset.ICLOUD.entryUri());
  }

  @Test
  @DisplayName("ICLOUD hint warns about the regular Apple ID password not working")
  void icloudHintMentions2faAndAppPassword() {
    String hint = CalDavProviderPreset.ICLOUD.hint();
    assertTrue(hint.contains("app-specific password"),
        "iCloud hint must point users at app-specific passwords");
    assertTrue(hint.contains("two-factor"),
        "iCloud hint must explain why the regular password fails");
    assertTrue(hint.contains("appleid.apple.com"),
        "iCloud hint must include the Apple ID portal URL");
  }

  @Test
  @DisplayName("each preset carries id, label, icon, entryUri, hint — none nullable")
  void recordRejectsNulls() {
    assertNotNull(CalDavProviderPreset.ICLOUD.id());
    assertNotNull(CalDavProviderPreset.ICLOUD.label());
    assertNotNull(CalDavProviderPreset.ICLOUD.icon());
    assertNotNull(CalDavProviderPreset.ICLOUD.entryUri());
    assertNotNull(CalDavProviderPreset.ICLOUD.hint());
    assertThrows(NullPointerException.class,
        () -> new CalDavProviderPreset(null, "x",
            com.vaadin.flow.component.icon.VaadinIcon.CLOUD, "u", "h"));
  }
}
