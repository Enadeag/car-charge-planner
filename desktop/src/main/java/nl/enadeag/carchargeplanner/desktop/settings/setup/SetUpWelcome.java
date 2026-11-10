package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.settings.form.ProviderTexts;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import java.util.List;
import java.util.function.Predicate;
import javafx.scene.control.Label;

final class SetUpWelcome implements SetUpStepPage {

    private final Messages messages;
    private final List<ProviderInfo> providers;
    private final Predicate<ProviderId> searchesPlaces;
    private final Label privacy;
    private final PageView view;

    /**
     * Says nothing about a provider until {@link #nameProviderOf(EngineConfig)}.
     *
     * @param searchesPlaces whether a provider has a place search, which is then sent what the owner types
     */
    SetUpWelcome(List<ProviderInfo> providers, Predicate<ProviderId> searchesPlaces, Messages messages) {
        this.messages = messages;
        this.providers = List.copyOf(providers);
        this.searchesPlaces = searchesPlaces;
        privacy = Labels.paragraph("", "note");
        view = new PageView(messages.text(SetUpStep.WELCOME.title()));
        view.show(Labels.paragraph(messages.text(TextKey.SET_UP_WELCOME_INTRO), "field-label"),
                new CardView(messages.text(TextKey.SET_UP_PRIVACY_TITLE), "", privacy),
                new CardView(messages.text(TextKey.SET_UP_TYPICAL_TITLE), messages.text(TextKey.SET_UP_TYPICAL)));
    }

    void nameProviderOf(EngineConfig config) {
        var texts = new ProviderTexts(messages);
        var primary = providers.stream().filter(info -> info.id().equals(config.forecast().primary())).findFirst();
        privacy.setText(primary.map(info -> messages.text(TextKey.SET_UP_PRIVACY, info.name(), texts.sends(info))
                + (searchesPlaces.test(info.id()) ? " " + messages.text(TextKey.SET_UP_PRIVACY_PLACE, info.name()) : ""))
                .orElse(""));
    }

    @Override
    public PageView view() {
        return view;
    }

    @Override
    public void onReached(SetUpForm form) {
        nameProviderOf(form.config());
    }
}
