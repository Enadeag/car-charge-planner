package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import nl.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypicalMarksTest {

    private static final String TAG = "Typical value";

    private final TypicalMarks marks = new TypicalMarks(TAG);
    /** The tag each followed field shows now. */
    private final Map<Setting, String> tags = new EnumMap<>(Setting.class);
    private final StringBuilder filled = new StringBuilder();

    TypicalMarksTest() {
        for (var setting : Set.of(Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION, Setting.CAR_DAILY_DISTANCE)) {
            marks.follow(setting, tag -> tags.put(setting, tag));
        }
    }

    /** Fills the fields as Settings does, with each field telling the marks that it changed. */
    private Runnable fill(String what) {
        return () -> {
            filled.append(what);
            tags.keySet().forEach(marks::edited);
        };
    }

    @Test
    void loadingTagsTheStoredTypicalValuesOfThisCardOnly() {
        marks.load(Set.of(Setting.CAR_BATTERY, Setting.HOUSE_BASELOAD), fill("stored"));

        assertThat(filled).hasToString("stored");
        assertThat(marks.marked()).containsExactly(Setting.CAR_BATTERY);
        assertThat(tags).containsEntry(Setting.CAR_BATTERY, TAG).containsEntry(Setting.CAR_CONSUMPTION, "")
                .containsEntry(Setting.CAR_DAILY_DISTANCE, "");
        assertThat(marks.isChanged()).isFalse();
    }

    @Test
    void anEditOfTheOwnersTakesItsTagAway() {
        marks.load(Set.of(Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION), fill("stored"));

        marks.edited(Setting.CAR_BATTERY);

        assertThat(tags).containsEntry(Setting.CAR_BATTERY, "").containsEntry(Setting.CAR_CONSUMPTION, TAG);
        assertThat(marks.marked()).containsExactly(Setting.CAR_CONSUMPTION);
        assertThat(marks.isChanged()).isTrue();
    }

    @Test
    void anEditOfAnUntaggedFieldLeavesItsTagAlone() {
        marks.load(Set.of(), fill("stored"));
        tags.put(Setting.CAR_BATTERY, "untouched");

        marks.edited(Setting.CAR_BATTERY);

        assertThat(tags).containsEntry(Setting.CAR_BATTERY, "untouched");
        assertThat(marks.isChanged()).isFalse();
    }

    @Test
    void askingForTypicalValuesFillsAndTagsEveryField() {
        marks.load(Set.of(), fill("stored"));

        marks.useTypical(fill(", typical"));

        assertThat(filled).hasToString("stored, typical");
        assertThat(marks.marked()).containsExactlyInAnyOrder(Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION,
                Setting.CAR_DAILY_DISTANCE);
        assertThat(tags.values()).containsOnly(TAG);
        assertThat(marks.isChanged()).isTrue();
    }

    @Test
    void showingTheSameValuesAnotherWayKeepsTheTags() {
        marks.load(Set.of(Setting.CAR_CONSUMPTION), fill("stored"));

        marks.keep(fill(", in miles"));

        assertThat(filled).hasToString("stored, in miles");
        assertThat(marks.marked()).containsExactly(Setting.CAR_CONSUMPTION);
        marks.edited(Setting.CAR_CONSUMPTION);
        assertThat(marks.marked()).as("after keeping, an edit counts again").isEmpty();
    }

    @Test
    void afterAFailedChangeAnEditCountsAgain() {
        marks.load(Set.of(Setting.CAR_CONSUMPTION), fill("stored"));

        assertThatIllegalStateException().isThrownBy(() -> marks.keep(() -> {
            throw new IllegalStateException("failed");
        }));
        marks.edited(Setting.CAR_CONSUMPTION);

        assertThat(marks.marked()).isEmpty();
    }

    @Test
    void theStoredTypicalSettingsTakeThisCardsTagsInPlaceOfItsOwn() {
        marks.load(Set.of(Setting.CAR_BATTERY), fill("stored"));
        marks.edited(Setting.CAR_BATTERY);
        marks.useTypical(fill(", typical"));
        marks.edited(Setting.CAR_DAILY_DISTANCE);

        var applied = marks.appliedTo(Set.of(Setting.CAR_BATTERY, Setting.HOUSE_BASELOAD));

        assertThat(applied).containsExactlyInAnyOrder(Setting.HOUSE_BASELOAD, Setting.CAR_BATTERY,
                Setting.CAR_CONSUMPTION);
    }

    @Test
    void loadingAgainMakesTheNewTagsTheStoredOnes() {
        marks.load(Set.of(Setting.CAR_BATTERY), fill("stored"));
        marks.useTypical(fill(", typical"));

        marks.load(Set.of(Setting.CAR_DAILY_DISTANCE), fill(", saved"));

        assertThat(marks.isChanged()).isFalse();
        assertThat(marks.marked()).containsExactly(Setting.CAR_DAILY_DISTANCE);
    }
}
