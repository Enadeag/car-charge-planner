package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.ForecastSelection;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
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
final class ForecastPage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final Map<ProviderId, ProviderInfo> known = new LinkedHashMap<>();
    private final Map<ProviderId, ProviderCard> cards = new LinkedHashMap<>();
    private final ChoiceField<ProviderId> primary;
    private final ChoiceField<Optional<ProviderId>> fallback;
    private final FieldRow fallbackRow;
    private final Label keysNote = FieldRow.paragraph("", "note");
    private final Card choice;
    private final Card keysCard;
    private final Path keysFile;
    private ForecastSelection stored;
    private Secrets storedKeys = Secrets.NONE;
    /** Whether the stored keys could not be read because the keyring is locked. */
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
    ForecastPage(List<ProviderInfo> providers, ConnectionTest tester, Consumer<URI> openLink, Path keysFile,
            Background background, Messages messages) {
        this.messages = messages;
        this.keysFile = keysFile;
        this.view = new PageView(messages.text(Category.FORECAST.title()));
        providers.forEach(info -> {
            known.put(info.id(), info);
            cards.put(info.id(), new ProviderCard(info, tester, openLink, background, messages));
        });
        var ids = List.copyOf(known.keySet());
        primary = new ChoiceField<>(ids, this::name);
        var fallbacks = new ArrayList<Optional<ProviderId>>();
        fallbacks.add(Optional.empty());
        ids.forEach(id -> fallbacks.add(Optional.of(id)));
        fallback = new ChoiceField<>(fallbacks,
                choice -> choice.map(this::name).orElseGet(() -> messages.text(TextKey.FALLBACK_NONE)));
        fallbackRow = FieldRow.of(messages, TextKey.FIELD_FALLBACK, TextKey.FIELD_FALLBACK_HINT, fallback);
        choice = new Card(messages.text(TextKey.CARD_PROVIDERS), messages.text(TextKey.CARD_PROVIDERS_HINT),
                FieldRow.of(messages, TextKey.FIELD_PRIMARY, TextKey.FIELD_PRIMARY_HINT, primary), fallbackRow);
        keysCard = new Card(messages.text(TextKey.CARD_KEYS), "", keysNote);
        primary.onChange(this::show);
        fallback.onChange(this::show);
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
        show();
    }

    /**
     * Shows the stored API keys and where they are kept. Call before {@link #load(EngineConfig)}, which fills the
     * cards. While the keyring is locked, the key fields without a value show as locked.
     *
     * @param keys the stored secret settings; none when they could not be read
     * @param storage where the keys are kept now; a locked keychain also says why they could not be read
     * @param problem why the keys could not be read, in words for the owner; empty when they could
     */
    void loadKeys(Secrets keys, KeyStorage storage, Optional<String> problem) {
        storedKeys = keys;
        keysLocked = storage == KeyStorage.LOCKED_KEYCHAIN;
        var where = switch (storage) {
            case KEYCHAIN -> messages.text(TextKey.KEYS_IN_KEYCHAIN);
            case LOCKED_KEYCHAIN -> messages.text(TextKey.KEYS_IN_LOCKED_KEYCHAIN);
            case FILE -> messages.text(TextKey.KEYS_IN_FILE, keysFile.toString());
        };
        // a locked keychain is the reason the keys could not be read, and the note says so in the owner's language
        keysNote.setText(problem.filter(text -> storage != KeyStorage.LOCKED_KEYCHAIN)
                .map(text -> messages.text(TextKey.KEYS_PROBLEM, text) + " " + where).orElse(where));
    }

    /**
     * Every provider's secret settings as the page holds them: the stored ones, with what the known providers' fields
     * hold laid over them.
     *
     * @return the secrets to keep
     */
    Secrets keys() {
        var all = new LinkedHashMap<>(storedKeys.byProvider());
        cards.forEach((id, card) -> all.put(id, card.secret()));
        return new Secrets(all);
    }

    /**
     * What the owner entered and emptied in the secret settings, to apply to the stored ones. A key that could not be
     * read is never emptied by this.
     *
     * @return the changes; empty when nothing differs
     */
    KeyChanges keyChanges() {
        return KeyChanges.between(storedKeys, keys());
    }

    /**
     * Whether the secret settings differ from the stored ones.
     *
     * @return true when saving has to store the keys
     */
    boolean keysChanged() {
        return !keyChanges().isEmpty();
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(Unavailable.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        fallbackRow.clearProblem();
        if (!isChanged()) {
            return Optional.of(config);
        }
        var complete = chosen().stream().allMatch(id -> !cards.containsKey(id) || cards.get(id).checkRequired());
        try {
            var settings = new LinkedHashMap<>(stored.settings());
            cards.forEach((id, card) -> settings.put(id, card.plain()));
            settings.values().removeIf(Map::isEmpty);
            var selection = new ForecastSelection(primary.value(), fallback.value(), settings);
            return complete ? Optional.of(config.withForecast(selection)) : Optional.empty();
        } catch (InvalidSetting e) {
            if (e.setting() == Setting.FALLBACK_PROVIDER) {
                fallbackRow.showProblem(ProblemTexts.of(e.rule(), messages));
            }
            return Optional.empty();
        }
    }

    private List<ProviderId> chosen() {
        var ids = new ArrayList<ProviderId>();
        ids.add(primary.value());
        fallback.value().ifPresent(ids::add);
        return ids;
    }

    /** Each provider's card under the role it is chosen for; one chosen for both shows once. */
    private void show() {
        if (stored == null) {
            return;
        }
        var nodes = new ArrayList<Node>();
        nodes.add(choice);
        chosen().stream().distinct().filter(cards::containsKey).forEach(id -> nodes.add(cards.get(id).view()));
        nodes.add(keysCard);
        view.show(nodes.toArray(Node[]::new));
    }

    /** A provider by its name; one the config names but this version does not know, by its id. */
    private String name(ProviderId id) {
        return Optional.ofNullable(known.get(id)).map(ProviderInfo::name)
                .orElseGet(() -> messages.text(TextKey.PROVIDER_UNKNOWN, id.value()));
    }
}
