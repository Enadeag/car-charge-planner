package nl.enadeag.carchargeplanner.desktop.settings.away;

import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.Optional;
import javafx.scene.layout.Region;

/** The weekly away periods, on a card of their own. */
public final class AwayPage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final WeeklyAwayCard away;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public AwayPage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.AWAY.title()));
        this.away = new WeeklyAwayCard(messages);
    }

    @Override
    public Category category() {
        return Category.AWAY;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        away.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return away.isChanged();
    }

    @Override
    public void load(EngineConfig config) {
        away.load(config);
        view.show(away.view());
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        return away.read(config);
    }

    @Override
    public boolean showProblems() {
        return away.showProblems();
    }
}
