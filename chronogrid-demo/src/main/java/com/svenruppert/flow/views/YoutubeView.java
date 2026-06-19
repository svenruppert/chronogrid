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
import com.svenruppert.flow.views.ui.TemplateBrand;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

/**
 * Public showcase view — embeds a curated YouTube clip in a polished
 * card surface. No authorization annotation: anyone can watch it.
 */
@Route(value = YoutubeView.PATH, layout = MainLayout.class)
public class YoutubeView extends Composite<VerticalLayout>
    implements I18nSupport {

  public static final String PATH = "youtube";

  public static final String VIDEO_ID = "CxCMIc5Bx18";
  public static final String EMBED_URL =
      "https://www.youtube.com/embed/" + VIDEO_ID;
  public static final String WATCH_URL =
      "https://www.youtube.com/watch?v=" + VIDEO_ID;

  // i18n keys
  private static final String K_EYEBROW = "youtube.hero.eyebrow";
  private static final String K_TITLE = "youtube.hero.title";
  private static final String K_LEDE = "youtube.hero.lede";
  private static final String K_CAPTION_TITLE = "youtube.caption.title";
  private static final String K_CAPTION_BODY = "youtube.caption.body";
  private static final String K_WATCH = "youtube.action.watch";

  public YoutubeView() {
    VerticalLayout root = getContent();
    root.setSizeFull();
    root.setSpacing(false);
    root.setPadding(false);
    root.getStyle().set("gap", "var(--lumo-space-xl)");
    root.setAlignItems(FlexComponent.Alignment.STRETCH);

    root.add(buildHero(), buildPlayer(), buildFooter());
  }

  private Div buildHero() {
    Div hero = new Div();
    hero.addClassName(TemplateBrand.CSS_HERO_SURFACE);

    Span eyebrow = new Span(VaadinIcon.PLAY_CIRCLE.create(),
        new Span(tr(K_EYEBROW, "Showcase video")));
    eyebrow.addClassName("app-hero-eyebrow");

    H1 title = new H1(tr(K_TITLE, "A short clip about the template"));
    title.addClassName("app-hero-title");

    Paragraph lede = new Paragraph(tr(K_LEDE,
        "Replace the embedded video id in YoutubeView.VIDEO_ID with "
            + "your own — a product walk-through, a demo, an investor "
            + "pitch. The card scales to the iframe's aspect ratio."));
    lede.addClassName("app-hero-lede");

    Div inner = new Div(eyebrow, title, lede);
    inner.getStyle().set("display", "flex");
    inner.getStyle().set("flex-direction", "column");
    inner.getStyle().set("align-items", "flex-start");
    inner.getStyle().set("position", "relative");
    inner.getStyle().set("z-index", "1");
    hero.add(inner);
    return hero;
  }

  private Div buildPlayer() {
    Div card = new Div();
    card.addClassName(TemplateBrand.CSS_CARD);
    card.getStyle().set("padding", "0");
    card.getStyle().set("overflow", "hidden");
    card.getStyle().set("max-width", "960px");
    card.getStyle().set("width", "100%");
    card.getStyle().set("margin", "0 auto");
    card.getStyle().set("aspect-ratio", "16 / 9");

    IFrame player = new IFrame(EMBED_URL);
    player.setSizeFull();
    player.getElement().setAttribute("allowfullscreen", true);
    player.getElement().setAttribute("frameborder", "0");
    player.getElement().setAttribute("allow",
        "accelerometer; autoplay; clipboard-write; encrypted-media; "
            + "gyroscope; picture-in-picture");
    card.add(player);
    return card;
  }

  private Div buildFooter() {
    Div footer = new Div();
    footer.getStyle().set("display", "flex");
    footer.getStyle().set("flex-direction", "column");
    footer.getStyle().set("gap", "var(--lumo-space-s)");
    footer.getStyle().set("align-items", "center");
    footer.getStyle().set("text-align", "center");
    footer.getStyle().set("padding", "0 var(--lumo-space-s)");

    H3 h = new H3(tr(K_CAPTION_TITLE, "About this clip"));
    h.getStyle().set("margin", "0");
    h.getStyle().set("font-size", "1.125rem");

    Paragraph body = new Paragraph(tr(K_CAPTION_BODY,
        "A quick demonstration. Subscribe on the channel for more — "
            + "and feel free to swap this video for your own walk-through."));
    body.addClassName(TemplateBrand.CSS_MUTED);
    body.getStyle().set("margin", "0");
    body.getStyle().set("max-width", "60ch");

    Anchor watch = new Anchor(WATCH_URL,
        VaadinIcon.PLAY_CIRCLE.create(),
        new Span(tr(K_WATCH, "Watch on YouTube")),
        VaadinIcon.EXTERNAL_LINK.create());
    watch.setTarget(AnchorTarget.BLANK);
    watch.getStyle().set("display", "inline-flex");
    watch.getStyle().set("align-items", "center");
    watch.getStyle().set("gap", "var(--lumo-space-xs)");
    watch.getStyle().set("color", "var(--app-brand-700)");
    watch.getStyle().set("font-weight", "600");
    watch.getStyle().set("text-decoration", "none");
    watch.getStyle().set("padding", "var(--lumo-space-xs) var(--lumo-space-m)");
    watch.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
    watch.getStyle().set("border-radius", "var(--app-radius-pill)");
    watch.getStyle().set("background", "var(--lumo-base-color)");

    HorizontalLayout actions = new HorizontalLayout(watch);
    actions.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
    actions.setWidthFull();

    footer.add(h, body, actions);
    return footer;
  }
}
