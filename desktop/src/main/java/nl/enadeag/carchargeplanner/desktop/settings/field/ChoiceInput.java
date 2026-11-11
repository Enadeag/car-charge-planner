package nl.enadeag.carchargeplanner.desktop.settings.field;

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
public final class ChoiceInput<T> implements Input {

    private final ComboBox<T> box = new ComboBox<>();
    private T loaded;

    /**
     * Makes the field, showing the first choice until {@link #load(Object)}.
     *
     * @param choices the choices in the order they are offered, none null; when there are none, the field shows only
     *     what it is loaded with
     * @param name what the owner reads for a choice, in their language
     */
    public ChoiceInput(List<T> choices, Function<T, String> name) {
        box.getItems().setAll(choices);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T choice) {
                return choice == null ? "" : name.apply(choice);
            }

            @Override
            public T fromString(String text) {
                return box.getItems().stream().filter(choice -> name.apply(choice).equals(text)).findFirst()
                        .orElse(null);
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
    public void load(T choice) {
        loaded = choice;
        box.setValue(choice);
    }

    /**
     * Shows no choice, only a prompt such as "Choose…", and remembers that as not edited, as for a new item whose value
     * the owner has to choose.
     *
     * @param prompt what the field shows until a choice is made, in the owner's language
     */
    public void loadNone(String prompt) {
        loaded = null;
        box.setPromptText(prompt);
        box.setValue(null);
    }

    /**
     * Offers other choices from now on, such as a list that holds a stored value only while it is the one chosen. The
     * choice shown stays, also when it is not among them, and nothing counts as changed by this alone.
     *
     * @param choices the choices in the order they are offered, none null
     */
    public void offer(List<T> choices) {
        var items = box.getItems();
        if (items.equals(choices)) {
            return;
        }
        if (items.containsAll(choices)) {
            // only taking away, which leaves the choice shown as it is, also while the owner's choice is still
            // being handled
            items.retainAll(choices);
        } else {
            var shown = box.getValue();
            items.setAll(choices);
            box.setValue(shown);
        }
    }

    /**
     * Shows a choice as if the owner made it, so it counts as changed when it differs from the loaded one.
     *
     * @param choice one of the choices the field was made with
     */
    public void set(T choice) {
        box.setValue(choice);
    }

    /**
     * The choice now.
     *
     * @return the chosen one of the choices; null after {@link #loadNone(String)} until the owner chooses
     */
    public T value() {
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
        box.valueProperty().addListener((_, _, _) -> action.run());
    }
}
