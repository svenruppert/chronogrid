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

package com.svenruppert.flow.views;

import com.svenruppert.flow.i18n.I18nSupport;
import com.svenruppert.flow.views.ui.FeatureCard;
import com.svenruppert.flow.views.ui.TemplateBrand;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.Composite;

/**
 * Public surface that explains what security capabilities the template
 * ships with. Reachable without an account — every visitor (potential
 * forker, security reviewer, prospective user) can read it.
 *
 * <p>Layered structure:
 * <ul>
 *   <li>Hero with eyebrow link to the upstream jSentinel project,</li>
 *   <li>Three concern-scoped sections (Identity, Access, Audit),</li>
 *   <li>Architecture note about the layered {@code BootstrapExtension}
 *       SPI,</li>
 *   <li>Footer link back to the jSentinel home page.</li>
 * </ul>
 */
@Route(value = SecurityFeaturesView.NAV, layout = MainLayout.class)
public class SecurityFeaturesView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String NAV = "security";

  /** Upstream project page. Single source — change here only. */
  public static final String JSENTINEL_URL = "https://8g8.eu/sentinel4j";

  // i18n keys — only the high-traffic copy. Feature-card bodies live
  // inline to keep the view's static structure scan-friendly; rebrand
  // by editing this file or the bundles.
  private static final String K_EYEBROW = "security.hero.eyebrow";
  private static final String K_TITLE = "security.hero.title";
  private static final String K_LEDE = "security.hero.lede";
  private static final String K_S_IDENTITY_TITLE = "security.section.identity.title";
  private static final String K_S_IDENTITY_SUB = "security.section.identity.subtitle";
  private static final String K_S_ACCESS_TITLE = "security.section.access.title";
  private static final String K_S_ACCESS_SUB = "security.section.access.subtitle";
  private static final String K_S_AUDIT_TITLE = "security.section.audit.title";
  private static final String K_S_AUDIT_SUB = "security.section.audit.subtitle";
  private static final String K_ARCH_TITLE = "security.architecture.title";
  private static final String K_ARCH_BODY = "security.architecture.body";
  private static final String K_FOOTER_LEAD = "security.footer.lead";
  private static final String K_FOOTER_LINK = "security.footer.link";

  // Feature-card keys — one title + one body per card.
  private static final String K_C_ARGON_T = "security.card.argon2id.title";
  private static final String K_C_ARGON_B = "security.card.argon2id.body";
  private static final String K_C_BOOT_T = "security.card.bootstrap.title";
  private static final String K_C_BOOT_B = "security.card.bootstrap.body";
  private static final String K_C_PREF_T = "security.card.preflight.title";
  private static final String K_C_PREF_B = "security.card.preflight.body";
  private static final String K_C_BRUTE_T = "security.card.bruteforce.title";
  private static final String K_C_BRUTE_B = "security.card.bruteforce.body";
  private static final String K_C_ROLES_T = "security.card.roles.title";
  private static final String K_C_ROLES_B = "security.card.roles.body";
  private static final String K_C_ANNO_T = "security.card.annotations.title";
  private static final String K_C_ANNO_B = "security.card.annotations.body";
  private static final String K_C_DRIFT_T = "security.card.drift.title";
  private static final String K_C_DRIFT_B = "security.card.drift.body";
  private static final String K_C_PROP_T = "security.card.propagation.title";
  private static final String K_C_PROP_B = "security.card.propagation.body";
  private static final String K_C_AUDIT_T = "security.card.audit.title";
  private static final String K_C_AUDIT_B = "security.card.audit.body";
  private static final String K_C_SESS_T = "security.card.sessions.title";
  private static final String K_C_SESS_B = "security.card.sessions.body";
  private static final String K_C_MUT_T = "security.card.mutation.title";
  private static final String K_C_MUT_B = "security.card.mutation.body";

  // Bootstrap layer rows
  private static final String K_L_DEF_N = "security.layer.default.name";
  private static final String K_L_DEF_D = "security.layer.default.desc";
  private static final String K_L_PER_N = "security.layer.persistence.name";
  private static final String K_L_PER_D = "security.layer.persistence.desc";
  private static final String K_L_HAR_N = "security.layer.hardening.name";
  private static final String K_L_HAR_D = "security.layer.hardening.desc";

  public SecurityFeaturesView() {
    VerticalLayout content = getContent();
    content.setSpacing(false);
    content.setPadding(false);
    content.getStyle().set("gap", "var(--lumo-space-xl)");
    content.setAlignItems(FlexComponent.Alignment.STRETCH);

    content.add(buildHero());
    content.add(buildSection(
        tr(K_S_IDENTITY_TITLE, "Identity & credentials"),
        tr(K_S_IDENTITY_SUB,
            "How the template proves a visitor is who they claim to be."),
        new FeatureCard(VaadinIcon.KEY,
            tr(K_C_ARGON_T, "Argon2id hashing"),
            tr(K_C_ARGON_B,
                "BouncyCastle modern profile (~250 ms per hash). PBKDF2 "
                    + "hashes from earlier installs are auto-rehashed on "
                    + "next successful login.")),
        new FeatureCard(VaadinIcon.LOCK,
            tr(K_C_BOOT_T, "First-admin bootstrap"),
            tr(K_C_BOOT_B,
                "One-time token printed at JVM start + persisted to "
                    + "./data/jsentinel/bootstrap.token. The setup form "
                    + "consumes the token, then disappears.")),
        new FeatureCard(VaadinIcon.BAN,
            tr(K_C_PREF_T, "Password preflight"),
            tr(K_C_PREF_B,
                "Local blocklist of the 19 most-common passwords plus a "
                    + "Have-I-Been-Pwned k-anonymity range check. "
                    + "Plaintext never leaves the JVM — only the first 5 "
                    + "SHA-1 hex chars are transmitted. Disabled in tests "
                    + "via the app.hibp.enabled system property; fail-open "
                    + "on network errors.")),
        new FeatureCard(VaadinIcon.FIRE,
            tr(K_C_BRUTE_T, "Brute-force protection"),
            tr(K_C_BRUTE_B,
                "LoginAttemptPolicy throttles repeated failures. "
                    + "Counter is per-subject, decays automatically."))));

    content.add(buildSection(
        tr(K_S_ACCESS_TITLE, "Authorization & access"),
        tr(K_S_ACCESS_SUB,
            "How the template decides which routes a subject may see."),
        new FeatureCard(VaadinIcon.USER_CHECK,
            tr(K_C_ROLES_T, "Role + permission catalog"),
            tr(K_C_ROLES_B,
                "AuthorizationRole enum (ADMIN, USER) → permission set "
                    + "via AppAuthorizationService. Permissions are the "
                    + "granular unit; roles are convenience bundles.")),
        new FeatureCard(VaadinIcon.EYE,
            tr(K_C_ANNO_T, "@VisibleFor / @RequiresPermission"),
            tr(K_C_ANNO_B,
                "Drop the annotation on a view, the LoginListener + "
                    + "RoleAccessEvaluator do the rest. Unrestricted "
                    + "views are public by default.")),
        new FeatureCard(VaadinIcon.WARNING,
            tr(K_C_DRIFT_T, "Drift detection (Phase-4c)"),
            tr(K_C_DRIFT_B,
                "Role changes bump the subject''s JSentinelVersion. "
                    + "The next click on the affected user''s session "
                    + "reroutes to /login — no stale grants survive an "
                    + "admin''s revoke.")),
        new FeatureCard(VaadinIcon.EXCHANGE,
            tr(K_C_PROP_T, "Token propagation (V00.74 API ready)"),
            tr(K_C_PROP_B,
                "The jSentinel 00.74 TokenCredentialStore + "
                    + "OutboundTokenStrategy stack is on the classpath. "
                    + "The moment the app starts calling a REST backend, "
                    + "a @PropagateToken annotation routes the subject''s "
                    + "Bearer / OIDC / refresh token into the outbound "
                    + "Authorization header — no manual HttpClient "
                    + "plumbing. VaadinSessionTokenCredentialStore "
                    + "already lives in the session, ready to be wired "
                    + "into a PropagationBootstrapExtension when "
                    + "needed."))));

    content.add(buildSection(
        tr(K_S_AUDIT_TITLE, "Audit & sessions"),
        tr(K_S_AUDIT_SUB,
            "What the template records, and how an operator inspects it."),
        new FeatureCard(VaadinIcon.RECORDS,
            tr(K_C_AUDIT_T, "Persistent audit log"),
            tr(K_C_AUDIT_B,
                "Every login, logout, role change, session event lands "
                    + "in an Eclipse-Store-backed log + an in-memory ring "
                    + "buffer for the live /audit grid.")),
        new FeatureCard(VaadinIcon.CONNECT,
            tr(K_C_SESS_T, "Session inventory"),
            tr(K_C_SESS_B,
                "/admin/sessions lists every active subject. Revoke is "
                    + "one click, persisted, audited.")),
        new FeatureCard(VaadinIcon.SHIELD,
            tr(K_C_MUT_T, "Mutation-hardened core"),
            tr(K_C_MUT_B,
                "PIT mutation tests with per-package coverage floors "
                    + "enforced in CI. Security/services/model sit at "
                    + "75-90 % mutation coverage."))));

    content.add(buildArchitectureNote());
    content.add(buildFooterLink());
  }

  // ── Hero ───────────────────────────────────────────────────────

  private Div buildHero() {
    Div hero = new Div();
    hero.addClassName(TemplateBrand.CSS_HERO_SURFACE);

    Anchor eyebrowLink = new Anchor(JSENTINEL_URL,
        VaadinIcon.SHIELD.create(),
        new Span(tr(K_EYEBROW, "Powered by jSentinel")),
        VaadinIcon.EXTERNAL_LINK.create());
    eyebrowLink.setTarget(AnchorTarget.BLANK);
    eyebrowLink.addClassName("app-hero-eyebrow");
    eyebrowLink.getStyle().set("text-decoration", "none");

    H1 title = new H1(tr(K_TITLE, "Security, wired in"));
    title.addClassName("app-hero-title");

    Paragraph lede = new Paragraph(tr(K_LEDE,
        "The template doesn''t roll its own auth. It composes "
            + "jSentinel — a production-grade Java security stack with "
            + "annotation-driven access control, persistent audit, drift "
            + "detection and a layered bootstrap SPI. Here''s what''s already "
            + "in place."));
    lede.addClassName("app-hero-lede");

    Div inner = new Div(eyebrowLink, title, lede);
    inner.getStyle().set("display", "flex");
    inner.getStyle().set("flex-direction", "column");
    inner.getStyle().set("align-items", "flex-start");
    inner.getStyle().set("position", "relative");
    inner.getStyle().set("z-index", "1");
    hero.add(inner);
    return hero;
  }

  // ── Sections ───────────────────────────────────────────────────

  private Div buildSection(String title, String subtitle,
                           FeatureCard... cards) {
    Div section = new Div();
    section.getStyle().set("display", "flex");
    section.getStyle().set("flex-direction", "column");
    section.getStyle().set("gap", "var(--lumo-space-m)");
    section.getStyle().set("padding", "0 var(--lumo-space-s)");

    H2 h = new H2(title);
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.5rem");
    h.getStyle().set("font-weight", "700");
    h.getStyle().set("letter-spacing", "-0.015em");

    Paragraph p = new Paragraph(subtitle);
    p.addClassName(TemplateBrand.CSS_MUTED);
    p.getStyle().set("margin", "0");
    p.getStyle().set("max-width", "60ch");

    FlexLayout grid = new FlexLayout(cards);
    grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
    grid.getStyle().set("gap", "var(--lumo-space-l)");
    grid.getStyle().set("margin-top", "var(--lumo-space-s)");

    section.add(h, p, grid);
    return section;
  }

  // ── Architecture note ──────────────────────────────────────────

  private Div buildArchitectureNote() {
    Div note = new Div();
    note.addClassName(TemplateBrand.CSS_CARD);
    note.getStyle().set("display", "flex");
    note.getStyle().set("flex-direction", "column");
    note.getStyle().set("gap", "var(--lumo-space-s)");

    H2 h = new H2(tr(K_ARCH_TITLE,
        "Layered bootstrap — additive by design"));
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.25rem");
    h.getStyle().set("font-weight", "600");

    Paragraph p = new Paragraph(tr(K_ARCH_BODY,
        "Three BootstrapExtension implementations are registered via "
            + "META-INF/services and applied in order(). Adding a "
            + "fourth layer (MFA, multi-tenant, …) is one new SPI "
            + "implementation + one line in services — the existing "
            + "code does not change."));
    p.addClassName(TemplateBrand.CSS_MUTED);
    p.getStyle().set("margin", "0");

    Div layers = new Div();
    layers.getStyle().set("display", "grid");
    layers.getStyle().set("grid-template-columns",
        "auto auto 1fr");
    layers.getStyle().set("gap",
        "var(--lumo-space-s) var(--lumo-space-l)");
    layers.getStyle().set("align-items", "baseline");
    layers.getStyle().set("margin-top", "var(--lumo-space-s)");
    addLayerRow(layers, "0",
        tr(K_L_DEF_N, "Default"),
        tr(K_L_DEF_D, "Ring-buffer audit + logging + PBKDF2 hashing"));
    addLayerRow(layers, "10",
        tr(K_L_PER_N, "Persistence"),
        tr(K_L_PER_D, "Eclipse-Store audit + session stores"));
    addLayerRow(layers, "20",
        tr(K_L_HAR_N, "Hardening"),
        tr(K_L_HAR_D, "Argon2id hashing + drift-detection wiring"));

    note.add(h, p, layers);
    return note;
  }

  private static void addLayerRow(Div parent, String order,
                                  String name, String desc) {
    Span ord = new Span("order=" + order);
    ord.getElement().getThemeList().add("badge contrast pill");
    Span nm = new Span(name);
    nm.getStyle().set("font-weight", "600");
    Span d = new Span(desc);
    d.getStyle().set("color", "var(--lumo-secondary-text-color)");
    parent.add(ord, nm, d);
  }

  // ── Footer link ────────────────────────────────────────────────

  private Div buildFooterLink() {
    Div footer = new Div();
    footer.getStyle().set("display", "flex");
    footer.getStyle().set("align-items", "center");
    footer.getStyle().set("justify-content", "center");
    footer.getStyle().set("gap", "var(--lumo-space-s)");
    footer.getStyle().set("padding",
        "var(--lumo-space-l) var(--lumo-space-s)");
    footer.getStyle().set("border-top",
        "1px solid var(--lumo-contrast-10pct)");

    Span lead = new Span(tr(K_FOOTER_LEAD,
        "Want to know more about the security stack?"));
    lead.addClassName(TemplateBrand.CSS_MUTED);

    Anchor link = new Anchor(JSENTINEL_URL,
        new Span(tr(K_FOOTER_LINK, "Visit the jSentinel project")),
        VaadinIcon.EXTERNAL_LINK.create());
    link.setTarget(AnchorTarget.BLANK);
    link.getStyle().set("display", "inline-flex");
    link.getStyle().set("align-items", "center");
    link.getStyle().set("gap", "var(--lumo-space-xs)");
    link.getStyle().set("color", "var(--app-brand-700)");
    link.getStyle().set("font-weight", "600");
    link.getStyle().set("text-decoration", "none");

    footer.add(lead, link);
    return footer;
  }
}
