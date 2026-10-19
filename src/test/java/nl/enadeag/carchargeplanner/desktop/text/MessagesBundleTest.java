package nl.enadeag.carchargeplanner.desktop.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Every language's bundle is complete and fits the English one, so no text falls back or breaks in any language. */
class MessagesBundleTest {

    /** A place such as {0} or {1,choice,...}, by its number and its type. */
    private static final Pattern PLACE = Pattern.compile("\\{\\s*(\\d+)\\s*(?:,\\s*(\\w+))?");
    /** A quote MessageFormat would take as the start of quoted text, where an apostrophe was meant. */
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

    /** The file itself, read as it is: a ResourceBundle would hide a missing key behind its parent's. */
    private static Properties file(Language language) throws IOException {
        var name = "messages_" + language.code() + ".properties";
        try (var in = MessagesBundleTest.class.getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            var properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static Set<String> places(String pattern) {
        var unquoted = pattern.replace("''", "").replaceAll("'[^']*'", "");
        return PLACE.matcher(unquoted).results()
                .map(match -> match.group(1) + (match.group(2) == null ? "" : ":" + match.group(2)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void eachLanguageHasEveryTextAndNoOthers(Language language) throws IOException {
        var keys = Arrays.stream(TextKey.values()).map(TextKey::name).collect(Collectors.toSet());

        assertThat(file(language).stringPropertyNames()).containsExactlyInAnyOrderElementsOf(keys);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void eachTextHasTheSamePlacesAsInEnglish(Language language) throws IOException {
        var english = file(Language.ENGLISH);
        var translated = file(language);

        for (var key : translated.stringPropertyNames()) {
            assertThat(places(translated.getProperty(key))).as("%s in %s", key, language)
                    .isEqualTo(places(english.getProperty(key, "")));
        }
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void eachTextIsAPatternThatKeepsItsApostrophes(Language language) throws IOException {
        var texts = file(language);

        for (var key : texts.stringPropertyNames()) {
            var text = texts.getProperty(key);
            assertThat(text).as("%s in %s", key, language).isNotBlank().doesNotContainPattern(LONE_APOSTROPHE);
            assertThatCode(() -> new MessageFormat(text, language.locale())).as("%s in %s", key, language)
                    .doesNotThrowAnyException();
        }
    }
}
