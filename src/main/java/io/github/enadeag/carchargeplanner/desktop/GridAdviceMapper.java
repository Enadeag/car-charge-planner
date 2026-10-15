package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import io.github.enadeag.carchargeplanner.engine.plan.GridAdvice;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.ZoneId;

/** The engine's grid advice in words, with the days named as the tabs name them. */
final class GridAdviceMapper {

    private final Messages messages;
    private final ZoneId zone;
    private final LocalDate today;

    /**
     * Makes the mapper for one plan.
     *
     * @param planned the plan whose time zone and day of making the advice is told in
     * @param messages the texts, in the owner's language
     */
    GridAdviceMapper(Outlook.Planned planned, Messages messages) {
        this.messages = messages;
        this.zone = planned.zone();
        this.today = planned.madeAt().atZone(zone).toLocalDate();
    }

    /**
     * The advice in words: how much, when, at what current, and why.
     *
     * @param advice the engine's advice; its window may be empty when no day has room for the charge
     * @return the advice's title and detail
     */
    GridAdviceModel map(GridAdvice advice) {
        var energy = messages.decimal(advice.energyKwh());
        var title = advice.window()
                .map(window -> messages.text(TextKey.ADVICE_TITLE_WINDOW, energy, day(window),
                        messages.span(window.start(), window.end(), zone)))
                .orElseGet(() -> messages.text(TextKey.ADVICE_TITLE_NO_WINDOW, energy, dipDay(advice)));
        var settings = advice.window()
                .map(window -> messages.text(TextKey.ADVICE_SET, window.amps(), messages.decimal(window.powerKw())))
                .orElseGet(() -> messages.text(TextKey.ADVICE_NO_ROOM));
        return new GridAdviceModel(messages.text(TextKey.ADVICE_LABEL), title, why(advice) + " " + settings);
    }

    private String why(GridAdvice advice) {
        var minimum = messages.whole(advice.socEndPct() + advice.deficitPct());
        if (advice.socEndPct() <= 0) {
            return messages.text(TextKey.ADVICE_RUNS_EMPTY, dipDay(advice), minimum);
        }
        return messages.text(TextKey.ADVICE_ENDS_LOW, dipDay(advice), messages.whole(advice.socEndPct()), minimum);
    }

    private String dipDay(GridAdvice advice) {
        return messages.inSentence(advice.day(), today);
    }

    private String day(Window window) {
        return messages.inSentence(window.start().atZone(zone).toLocalDate(), today);
    }
}
