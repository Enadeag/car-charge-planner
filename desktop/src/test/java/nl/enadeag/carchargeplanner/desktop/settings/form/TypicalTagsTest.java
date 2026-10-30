package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypicalTagsTest {

    private static final String TAG = "Typical value";

    private final TypicalTags typicalTags = new TypicalTags(TAG);
    /** The tag each followed field shows now. */
    private final Map<ConfigSetting, String> tags = new EnumMap<>(ConfigSetting.class);
    private final StringBuilder filled = new StringBuilder();

    TypicalTagsTest() {
        for (var setting : Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION, ConfigSetting.CAR_DAILY_DISTANCE)) {
            typicalTags.follow(setting, tag -> tags.put(setting, tag));
        }
    }

    /** Fills the fields as Settings does, with each field telling the typical tags that it changed. */
    private Runnable fill(String what) {
        return () -> {
            filled.append(what);
            tags.keySet().forEach(typicalTags::edited);
        };
    }

    @Test
    void loadingTagsTheStoredTypicalValuesOfThisCardOnly() {
        typicalTags.load(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD), fill("stored"));

        assertThat(filled).hasToString("stored");
        assertThat(typicalTags.tagged()).containsExactly(ConfigSetting.CAR_BATTERY);
        assertThat(tags).containsEntry(ConfigSetting.CAR_BATTERY, TAG).containsEntry(ConfigSetting.CAR_CONSUMPTION, "")
                .containsEntry(ConfigSetting.CAR_DAILY_DISTANCE, "");
        assertThat(typicalTags.isChanged()).isFalse();
    }

    @Test
    void anEditOfTheOwnersTakesItsTagAway() {
        typicalTags.load(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        typicalTags.edited(ConfigSetting.CAR_BATTERY);

        assertThat(tags).containsEntry(ConfigSetting.CAR_BATTERY, "").containsEntry(ConfigSetting.CAR_CONSUMPTION, TAG);
        assertThat(typicalTags.tagged()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        assertThat(typicalTags.isChanged()).isTrue();
    }

    @Test
    void anEditOfAnUntaggedFieldLeavesItsTagAlone() {
        typicalTags.load(Set.of(), fill("stored"));
        tags.put(ConfigSetting.CAR_BATTERY, "untouched");

        typicalTags.edited(ConfigSetting.CAR_BATTERY);

        assertThat(tags).containsEntry(ConfigSetting.CAR_BATTERY, "untouched");
        assertThat(typicalTags.isChanged()).isFalse();
    }

    @Test
    void askingForTypicalValuesFillsAndTagsEveryField() {
        typicalTags.load(Set.of(), fill("stored"));

        typicalTags.useTypical(fill(", typical"));

        assertThat(filled).hasToString("stored, typical");
        assertThat(typicalTags.tagged()).containsExactlyInAnyOrder(ConfigSetting.CAR_BATTERY,
                ConfigSetting.CAR_CONSUMPTION, ConfigSetting.CAR_DAILY_DISTANCE);
        assertThat(tags.values()).containsOnly(TAG);
        assertThat(typicalTags.isChanged()).isTrue();
    }

    @Test
    void showingTheSameValuesAnotherWayKeepsTheTags() {
        typicalTags.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        typicalTags.keepTagsWhile(fill(", in miles"));

        assertThat(filled).hasToString("stored, in miles");
        assertThat(typicalTags.tagged()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
        typicalTags.edited(ConfigSetting.CAR_CONSUMPTION);
        assertThat(typicalTags.tagged()).as("after keeping, an edit counts again").isEmpty();
    }

    @Test
    void afterAFailedChangeAnEditCountsAgain() {
        typicalTags.load(Set.of(ConfigSetting.CAR_CONSUMPTION), fill("stored"));

        assertThatIllegalStateException().isThrownBy(() -> typicalTags.keepTagsWhile(() -> {
            throw new IllegalStateException("failed");
        }));
        typicalTags.edited(ConfigSetting.CAR_CONSUMPTION);

        assertThat(typicalTags.tagged()).isEmpty();
    }

    @Test
    void theStoredTypicalSettingsTakeThisCardsTagsInPlaceOfItsOwn() {
        typicalTags.load(Set.of(ConfigSetting.CAR_BATTERY), fill("stored"));
        typicalTags.edited(ConfigSetting.CAR_BATTERY);
        typicalTags.useTypical(fill(", typical"));
        typicalTags.edited(ConfigSetting.CAR_DAILY_DISTANCE);

        var applied = typicalTags.appliedTo(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.HOUSE_BASELOAD));

        assertThat(applied).containsExactlyInAnyOrder(ConfigSetting.HOUSE_BASELOAD, ConfigSetting.CAR_BATTERY,
                ConfigSetting.CAR_CONSUMPTION);
    }

    @Test
    void loadingAgainMakesTheNewTagsTheStoredOnes() {
        typicalTags.load(Set.of(ConfigSetting.CAR_BATTERY), fill("stored"));
        typicalTags.useTypical(fill(", typical"));

        typicalTags.load(Set.of(ConfigSetting.CAR_DAILY_DISTANCE), fill(", saved"));

        assertThat(typicalTags.isChanged()).isFalse();
        assertThat(typicalTags.tagged()).containsExactly(ConfigSetting.CAR_DAILY_DISTANCE);
    }
}
