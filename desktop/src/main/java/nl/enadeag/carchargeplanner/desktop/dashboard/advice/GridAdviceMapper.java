package nl.enadeag.carchargeplanner.desktop.dashboard.advice;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.GridAdviceView;
import java.time.ZoneId;

/** The engine's grid advice in words, with the days named as the tabs name them. */
public final class GridAdviceMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final ChargeLevels levels;

    /**
     * Makes the mapper for one home: its zone, the plan's levels and the texts.
     *
     * @param zone the home's time zone, in which the window's times are told; never null
     * @param levels the plan's charge levels, whose minimum the advice's end level is told against; never null
     * @param messages the texts, in the owner's language; never null
     */
    public GridAdviceMapper(ZoneId zone, ChargeLevels levels, Messages messages) {
        this.messages = messages;
        this.zone = zone;
        this.levels = levels;
    }

    /**
     * The advice in words: how much, when, at what current and charge limit, and why.
     *
     * @param advice the engine's advice, as its dashboard shows it; its window may be empty when no day has room for
     *     the charge; never null
     * @return the advice's label, its title (how much and when) and its detail (why, and what to set)
     */
    public GridAdviceModel map(GridAdviceView advice) {
        var energy = messages.decimal(advice.energyKwh());
        var title = advice.window()
                .map(window -> messages.text(TextKey.ADVICE_TITLE_WINDOW, energy, messages.inSentence(window.day()),
                        messages.span(window.window().start(), window.window().end(), zone)))
                .orElseGet(() -> messages.text(TextKey.ADVICE_TITLE_NO_WINDOW, energy,
                        messages.inSentence(advice.day())));
        var settings = advice.window()
                .map(window -> messages.text(TextKey.ADVICE_SET, window.window().amps(),
                        messages.decimal(window.window().powerKw()), messages.whole(window.chargeLimitPct().value())))
                .orElseGet(() -> messages.text(TextKey.ADVICE_NO_ROOM));
        return new GridAdviceModel(messages.text(TextKey.ADVICE_LABEL), title, why(advice) + " " + settings);
    }

    private String why(GridAdviceView advice) {
        var day = messages.inSentence(advice.day());
        var minimum = messages.whole(levels.minimumPct().value());
        if (advice.runsEmpty()) {
            return messages.text(TextKey.ADVICE_RUNS_EMPTY, day, minimum);
        }
        return messages.text(TextKey.ADVICE_ENDS_LOW, day, messages.whole(advice.endLevelPct()), minimum);
    }
}
