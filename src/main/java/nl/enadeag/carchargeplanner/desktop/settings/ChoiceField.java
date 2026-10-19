package nl.enadeag.carchargeplanner.desktop.settings;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/**
 * One of a fixed set of choices, such as the units or the language.
 *
 * @param <T> the type of the choices, such as an enum
 */
final class ChoiceField<T> implements Input {

    private final ComboBox<T> box = new ComboBox<>();
    private T loaded;

    /**
     * Makes the field, showing the first choice until {@link #load(Object)}.
     *
     * @param choices the choices in the order they are offered, none null; when there are none, the field shows only
     *     what it is loaded with
     * @param name what the owner reads for a choice, in their language
     */
    ChoiceField(List<T> choices, Function<T, String> name) {
        box.getItems().setAll(choices);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T choice) {
                return choice == null ? "" : name.apply(choice);
            }

            @Override
            public T fromString(String text) {
                return choices.stream().filter(choice -> name.apply(choice).equals(text)).findFirst().orElse(null);
            }
        });
        box.getStyleClass().add("choice-field");
        loaded = choices.isEmpty() ? null : choices.getFirst();
        box.setValue(loaded);
    }

    /**
     * Shows a choice and remembers it as the one not edited.
     *
     * @param choice one of the choices the field was made with
     */
    void load(T choice) {
        loaded = choice;
        box.setValue(choice);
    }

    /**
     * Shows a choice as if the owner made it, so it counts as changed when it differs from the loaded one.
     *
     * @param choice one of the choices the field was made with
     */
    void set(T choice) {
        box.setValue(choice);
    }

    /**
     * The choice now.
     *
     * @return the chosen one of the choices
     */
    T value() {
        return box.getValue();
    }

    @Override
    public Node view() {
        return box;
    }

    @Override
    public Node focusable() {
        return box;
    }

    @Override
    public boolean isChanged() {
        return !Objects.equals(box.getValue(), loaded);
    }

    @Override
    public void onChange(Runnable action) {
        box.valueProperty().addListener((property, before, now) -> action.run());
    }
}
