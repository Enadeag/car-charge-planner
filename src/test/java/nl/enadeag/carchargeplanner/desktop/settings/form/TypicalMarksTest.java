package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypicalMarksTest {

    private static final String TAG = "Typical value";

    private final TypicalMarks marks = new TypicalMarks(TAG);
    /** The tag each followed field shows now. */
    private final Map<ConfigSetting, String> tags = new EnumMap<>(ConfigSetting.class);
    private final StringBuilder filled = new StringBuilder();

    TypicalMarksTest() {
        for (var setting : Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION, ConfigSetting.CAR_DAILY_DISTANCE)) {
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
        marks.load(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD), fill("stored"));

        assertThat(filled).hasToString("stored");
        assertThat(marks.marked()).containsExactly(ConfigSetting.CAR_BATTERY);
        assertThat(tags).containsEntry(ConfigSetting.CAR_BATTERY, TAG).containsEntry(ConfigSetting.CAR_CONSUMPTION, "")
                .containsEntry(ConfigSetting.CAR_DAILY_DISTANCE, "");
        assertThat(marks.isChanged()).isFalse();
    }

    @Test
    void anEditOfTheOwnersTakesItsTagAway() {
        marks.load(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        marks.edited(ConfigSetting.CAR_BATTERY);

        assertThat(tags).containsEntry(ConfigSetting.CAR_BATTERY, "").containsEntry(ConfigSetting.CAR_CONSUMPTION, TAG);
        assertThat(marks.marked()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        assertThat(marks.isChanged()).isTrue();
    }

    @Test
    void anEditOfAnUntaggedFieldLeavesItsTagAlone() {
        marks.load(Set.of(), fill("stored"));
        tags.put(ConfigSetting.CAR_BATTERY, "untouched");

        marks.edited(ConfigSetting.CAR_BATTERY);

        assertThat(tags).containsEntry(ConfigSetting.CAR_BATTERY, "untouched");
        assertThat(marks.isChanged()).isFalse();
    }

    @Test
    void askingForTypicalValuesFillsAndTagsEveryField() {
        marks.load(Set.of(), fill("stored"));

        marks.useTypical(fill(", typical"));

        assertThat(filled).hasToString("stored, typical");
        assertThat(marks.marked()).containsExactlyInAnyOrder(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION,
                ConfigSetting.CAR_DAILY_DISTANCE);
        assertThat(tags.values()).containsOnly(TAG);
        assertThat(marks.isChanged()).isTrue();
    }

    @Test
    void showingTheSameValuesAnotherWayKeepsTheTags() {
        marks.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        marks.keep(fill(", in miles"));

        assertThat(filled).hasToString("stored, in miles");
        assertThat(marks.marked()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        marks.edited(ConfigSetting.CAR_CONSUMPTION);
        assertThat(marks.marked()).as("after keeping, an edit counts again").isEmpty();
    }

    @Test
    void afterAFailedChangeAnEditCountsAgain() {
        marks.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        assertThatIllegalStateException().isThrownBy(() -> marks.keep(() -> {
            throw new IllegalStateException("failed");
        }));
        marks.edited(ConfigSetting.CAR_CONSUMPTION);

        assertThat(marks.marked()).isEmpty();
    }

    @Test
    void theStoredTypicalSettingsTakeThisCardsTagsInPlaceOfItsOwn() {
        marks.load(Set.of(ConfigSetting.CAR_BATTERY), fill("stored"));
        marks.edited(ConfigSetting.CAR_BATTERY);
        marks.useTypical(fill(", typical"));
        marks.edited(ConfigSetting.CAR_DAILY_DISTANCE);

        var applied = marks.appliedTo(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD));

        assertThat(applied).containsExactlyInAnyOrder(ConfigSetting.HOUSE_BASELOAD, ConfigSetting.CAR_BATTERY,
                ConfigSetting.CAR_CONSUMPTION);
    }

    @Test
    void loadingAgainMakesTheNewTagsTheStoredOnes() {
        marks.load(Set.of(ConfigSetting.CAR_BATTERY), fill("stored"));
        marks.useTypical(fill(", typical"));

        marks.load(Set.of(ConfigSetting.CAR_DAILY_DISTANCE), fill(", saved"));

        assertThat(marks.isChanged()).isFalse();
        assertThat(marks.marked()).containsExactly(ConfigSetting.CAR_DAILY_DISTANCE);
    }
}
