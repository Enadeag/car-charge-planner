package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypicalMarksTest {

    private static final Set<ConfigSetting> FOLLOWED = Set.of(ConfigSetting.CAR_BATTERY,
            ConfigSetting.CAR_CONSUMPTION, ConfigSetting.CAR_DAILY_DISTANCE);

    private final TypicalMarks typicalMarks = new TypicalMarks();
    private final StringBuilder filled = new StringBuilder();

    TypicalMarksTest() {
        FOLLOWED.forEach(typicalMarks::follow);
    }

    /** Fills the fields as Settings does, with each field telling the typical marks that it changed. */
    private Runnable fill(String what) {
        return () -> {
            filled.append(what);
            FOLLOWED.forEach(typicalMarks::edited);
        };
    }

    @Test
    void loadingMarksTheStoredTypicalValuesOfThisCardOnly() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD), fill("stored"));

        assertThat(filled).hasToString("stored");
        assertThat(typicalMarks.marked()).containsExactly(ConfigSetting.CAR_BATTERY);
        assertThat(typicalMarks.isChanged()).isFalse();
    }

    @Test
    void anEditOfTheOwnersTakesItsMarkAway() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        typicalMarks.edited(ConfigSetting.CAR_BATTERY);

        assertThat(typicalMarks.marked()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        assertThat(typicalMarks.isChanged()).isTrue();
    }

    @Test
    void anEditOfAnUnmarkedFieldChangesNoMark() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        typicalMarks.edited(ConfigSetting.CAR_BATTERY);

        assertThat(typicalMarks.marked()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        assertThat(typicalMarks.isChanged()).isFalse();
    }

    @Test
    void askingForTypicalValuesFillsAndMarksEveryField() {
        typicalMarks.load(Set.of(), fill("stored"));

        typicalMarks.useTypical(fill(", typical"));

        assertThat(filled).hasToString("stored, typical");
        assertThat(typicalMarks.marked()).containsExactlyInAnyOrderElementsOf(FOLLOWED);
        assertThat(typicalMarks.isChanged()).isTrue();
    }

    @Test
    void askingForSomeTypicalValuesMarksTheFollowedOnesOfThemAndKeepsTheOtherMarks() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_DAILY_DISTANCE), fill("stored"));

        typicalMarks.useTypical(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD), fill(", typical"));

        assertThat(filled).hasToString("stored, typical");
        assertThat(typicalMarks.marked()).containsExactlyInAnyOrder(ConfigSetting.CAR_BATTERY,
                ConfigSetting.CAR_DAILY_DISTANCE);
    }

    @Test
    void showingTheSameValuesAnotherWayKeepsTheMarks() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        typicalMarks.keepMarksWhile(fill(", in miles"));

        assertThat(filled).hasToString("stored, in miles");
        assertThat(typicalMarks.marked()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        typicalMarks.edited(ConfigSetting.CAR_CONSUMPTION);
        assertThat(typicalMarks.marked()).as("after keeping, an edit counts again").isEmpty();
    }

    @Test
    void afterAFailedChangeAnEditCountsAgain() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        assertThatIllegalStateException().isThrownBy(() -> typicalMarks.keepMarksWhile(() -> {
            throw new IllegalStateException("failed");
        }));
        typicalMarks.edited(ConfigSetting.CAR_CONSUMPTION);

        assertThat(typicalMarks.marked()).isEmpty();
    }

    @Test
    void theStoredTypicalSettingsTakeThisCardsMarksInPlaceOfItsOwn() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_BATTERY), fill("stored"));
        typicalMarks.edited(ConfigSetting.CAR_BATTERY);
        typicalMarks.useTypical(fill(", typical"));
        typicalMarks.edited(ConfigSetting.CAR_DAILY_DISTANCE);

        var applied = typicalMarks.appliedTo(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD));

        assertThat(applied).containsExactlyInAnyOrder(ConfigSetting.HOUSE_BASELOAD, ConfigSetting.CAR_BATTERY,
                ConfigSetting.CAR_CONSUMPTION);
    }

    @Test
    void loadingAgainMakesTheNewMarksTheStoredOnes() {
        typicalMarks.load(Set.of(ConfigSetting.CAR_BATTERY), fill("stored"));
        typicalMarks.useTypical(fill(", typical"));

        typicalMarks.load(Set.of(ConfigSetting.CAR_DAILY_DISTANCE), fill(", saved"));

        assertThat(typicalMarks.isChanged()).isFalse();
        assertThat(typicalMarks.marked()).containsExactly(ConfigSetting.CAR_DAILY_DISTANCE);
    }
}
