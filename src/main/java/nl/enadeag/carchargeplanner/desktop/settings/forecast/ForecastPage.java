package nl.enadeag.carchargeplanner.desktop.settings.forecast;

import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.settings.form.ForecastEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.KeysNote;
import nl.enadeag.carchargeplanner.desktop.settings.form.ProblemTexts;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.ForecastSelection;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * The forecast providers: the primary one, the fallback asked only when the primary has no usable forecast, each with
 * its settings, and where their API keys are kept. Plain settings go to the config file, secret ones to the keychain.
 */
public final class ForecastPage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final Map<ProviderId, ProviderInfo> known = new LinkedHashMap<>();
    private final Map<ProviderId, ProviderCard> cards = new LinkedHashMap<>();
    private final ChoiceInput<ProviderId> primary;
    private final ChoiceInput<Optional<ProviderId>> fallback;
    private final FieldRow fallbackRow;
    private final Label keysNote = Labels.paragraph("", "note");
    private final CardView choice;
    private final CardView keysCard;
    private final Path keysFile;
    private ForecastSelection stored;
    private Secrets storedKeys = Secrets.NONE;
    /** Whether the stored keys could not be read because the keychain is locked. */
    private boolean keysLocked;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param providers what the known providers tell about themselves, in the order they are offered; at least one
     * @param tester asks a provider for a forecast with what is entered
     * @param openLink opens a web page in the owner's browser
     * @param keysFile the file the keys are kept in when there is no usable keychain
     * @param background asks the providers away from the JavaFX thread
     * @param messages the texts, in the owner's language
     */
    public ForecastPage(List<ProviderInfo> providers, ProviderTester tester, Consumer<URI> openLink, Path keysFile,
            Background background, Messages messages) {
        this.messages = messages;
        this.keysFile = keysFile;
        this.view = new PageView(messages.text(Category.FORECAST.title()));
        providers.forEach(info -> {
            known.put(info.id(), info);
            cards.put(info.id(), new ProviderCard(info, tester, openLink, background, messages));
        });
        var ids = List.copyOf(known.keySet());
        var names = new ChoiceNames(messages);
        var infos = List.copyOf(known.values());
        primary = new ChoiceInput<>(ids, id -> names.provider(id, infos));
        var fallbacks = new ArrayList<Optional<ProviderId>>();
        fallbacks.add(Optional.empty());
        ids.forEach(id -> fallbacks.add(Optional.of(id)));
        fallback = new ChoiceInput<>(fallbacks,
                choice -> choice.map(id -> names.provider(id, infos)).orElseGet(names::noFallback));
        fallbackRow = FieldRow.of(messages, TextKey.FIELD_FALLBACK, TextKey.FIELD_FALLBACK_HINT, fallback);
        choice = new CardView(messages.text(TextKey.CARD_PROVIDERS), messages.text(TextKey.CARD_PROVIDERS_HINT),
                FieldRow.of(messages, TextKey.FIELD_PRIMARY, TextKey.FIELD_PRIMARY_HINT, primary), fallbackRow);
        keysCard = new CardView(messages.text(TextKey.CARD_KEYS), "", keysNote);
        primary.onChange(this::showChosenCards);
        fallback.onChange(this::showChosenCards);
    }

    @Override
    public Category category() {
        return Category.FORECAST;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        primary.onChange(action);
        fallback.onChange(action);
        cards.values().forEach(card -> card.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return primary.isChanged() || fallback.isChanged() || cards.values().stream().anyMatch(ProviderCard::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        stored = config.forecast();
        primary.load(stored.primary());
        fallback.load(stored.fallback());
        fallbackRow.clearProblem();
        cards.forEach((id, card) -> card.load(stored.settingsOf(id), storedKeys.of(id), keysLocked));
        showChosenCards();
    }

    /**
     * Shows the stored API keys and where they are kept. Call before {@link #load(EngineConfig)}, which fills the
     * cards. While the keychain is locked, the key fields without a value show as locked.
     *
     * @param keys the stored secret settings
     * @param storage where the keys are kept now
     */
    public void loadKeys(Secrets keys, KeyStorage storage) {
        loadKeys(keys, storage, KeysNote.of(storage, keysFile, messages));
    }

    /**
     * Shows that the API keys could not be read, and where they are kept, as {@link #loadKeys(Secrets, KeyStorage)}.
     *
     * @param keys the secret settings to show; none, as they could not be read
     * @param storage where the keys are kept now; a locked keychain also says why they could not be read
     * @param problem the kind of problem that kept the keys from being read
     */
    public void loadKeys(Secrets keys, KeyStorage storage, SecretsProblem.Kind problem) {
        loadKeys(keys, storage, KeysNote.of(storage, problem, keysFile, messages));
    }

    private void loadKeys(Secrets keys, KeyStorage storage, String note) {
        storedKeys = keys;
        keysLocked = storage == KeyStorage.LOCKED_KEYCHAIN;
        keysNote.setText(note);
    }

    /**
     * Every provider's secret settings as the page holds them: the stored ones, with what the known providers' fields
     * hold laid over them.
     *
     * @return the secrets to keep
     */
    public Secrets keys() {
        var all = new LinkedHashMap<>(storedKeys.byProvider());
        cards.forEach((id, card) -> all.put(id, card.secret()));
        return new Secrets(all);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            return Optional.of(config);
        }
        var entry = entry();
        if (entry.chosen().stream().filter(cards::containsKey).anyMatch(id -> cards.get(id).isRefused())) {
            return Optional.empty();
        }
        return selection(entry, _ -> { }).map(config::withForecast);
    }

    @Override
    public boolean showProblems() {
        fallbackRow.clearProblem();
        if (!isChanged()) {
            return false;
        }
        var entry = entry();
        // every chosen card marks its own problems, also when an earlier one is refused already
        var refused = entry.chosen().stream().filter(cards::containsKey).map(id -> cards.get(id).markRefused())
                .toList();
        var selection = selection(entry, e -> {
            if (e.setting() == ConfigSetting.FALLBACK_PROVIDER) {
                fallbackRow.showProblem(ProblemTexts.of(e.rule(), messages));
            }
        });
        return refused.contains(true) || selection.isEmpty();
    }

    /** The choice of providers with their settings as entered, after its record checked it. */
    private Optional<ForecastSelection> selection(ForecastEntry entry, Consumer<InvalidSetting> refused) {
        try {
            var shown = new LinkedHashMap<ProviderId, Map<String, String>>();
            cards.forEach((id, card) -> shown.put(id, card.plain()));
            return Optional.of(entry.selection(stored.settings(), shown));
        } catch (InvalidSetting e) {
            refused.accept(e);
            return Optional.empty();
        }
    }

    private ForecastEntry entry() {
        return new ForecastEntry(primary.value(), fallback.value());
    }

    /** Each provider's card under the role it is chosen for; one chosen for both shows once. */
    private void showChosenCards() {
        if (stored == null) {
            return;
        }
        var nodes = new ArrayList<Node>();
        nodes.add(choice);
        entry().chosen().stream().filter(cards::containsKey).forEach(id -> nodes.add(cards.get(id).view()));
        nodes.add(keysCard);
        view.show(nodes.toArray(Node[]::new));
    }
}
