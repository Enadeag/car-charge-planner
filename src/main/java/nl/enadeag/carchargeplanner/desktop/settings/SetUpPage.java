package nl.enadeag.carchargeplanner.desktop.settings;

import java.util.function.Consumer;
import javafx.scene.layout.Region;

/** What one step of set-up shows, and what it does each time the owner reaches it. */
interface SetUpPage {

    /**
     * The step's page, with the step's name as its title.
     *
     * @return the same node every time
     */
    Region view();

    /**
     * Called each time the step is shown, such as to ask for a forecast with what was entered so far.
     *
     * @param form what set-up holds now; the steps before this one are checked
     */
    default void enter(SetUpForm form) {
    }

    /**
     * A step that only shows its page.
     *
     * @param view the page, such as one of Settings'
     * @return the step's page
     */
    static SetUpPage of(Region view) {
        return of(view, _ -> { });
    }

    /**
     * A step that shows a page and acts as the owner reaches it.
     *
     * @param view the page, such as one of Settings'
     * @param entered what to do each time the step is shown, on the JavaFX thread
     * @return the step's page
     */
    static SetUpPage of(Region view, Consumer<SetUpForm> entered) {
        return new SetUpPage() {
            @Override
            public Region view() {
                return view;
            }

            @Override
            public void enter(SetUpForm form) {
                entered.accept(form);
            }
        };
    }
}
