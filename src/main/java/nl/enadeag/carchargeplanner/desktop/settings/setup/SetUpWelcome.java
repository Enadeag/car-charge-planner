package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.form.ProviderTexts;
import nl.enadeag.carchargeplanner.desktop.settings.page.Card;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import java.util.List;
import java.util.function.Predicate;
import javafx.scene.control.Label;

/** Set-up's first step: what the app does, what leaves the computer, and what to do with a value one does not know. */
final class SetUpWelcome implements SetUpPage {

    private final Messages messages;
    private final List<ProviderInfo> providers;
    private final Predicate<ProviderId> searchesPlaces;
    private final Label privacy;
    private final PageView view;

    /**
     * Makes the step, saying nothing about a provider until {@link #load(EngineConfig)}.
     *
     * @param providers what the known providers tell about themselves, to name the chosen one
     * @param searchesPlaces whether a provider has a place search, which is then sent what the owner types
     * @param messages the texts, in the language set-up speaks
     */
    SetUpWelcome(List<ProviderInfo> providers, Predicate<ProviderId> searchesPlaces, Messages messages) {
        this.messages = messages;
        this.providers = List.copyOf(providers);
        this.searchesPlaces = searchesPlaces;
        privacy = FieldRow.paragraph("", "note");
        view = new PageView(messages.text(SetUpStep.WELCOME.title()));
        view.show(FieldRow.paragraph(messages.text(TextKey.SETUP_WELCOME_INTRO), "field-label"),
                new Card(messages.text(TextKey.SETUP_PRIVACY_TITLE), "", privacy),
                new Card(messages.text(TextKey.SETUP_TYPICAL_TITLE), messages.text(TextKey.SETUP_TYPICAL)));
    }

    /**
     * Names the primary provider of a draft, and what it is sent.
     *
     * @param config the draft set-up starts from
     */
    void load(EngineConfig config) {
        var texts = new ProviderTexts(messages);
        var primary = providers.stream().filter(info -> info.id().equals(config.forecast().primary())).findFirst();
        privacy.setText(primary.map(info -> messages.text(TextKey.SETUP_PRIVACY, info.name(), texts.sends(info))
                + (searchesPlaces.test(info.id()) ? " " + messages.text(TextKey.SETUP_PRIVACY_PLACE, info.name()) : ""))
                .orElse(""));
    }

    @Override
    public PageView view() {
        return view;
    }

    @Override
    public void enter(SetUpForm form) {
        load(form.config());
    }
}
