package nl.enadeag.carchargeplanner.desktop.settings.setup;

import java.util.function.Consumer;
import javafx.scene.layout.Region;

/** What one step of set-up shows, and what it does each time the owner reaches it. */
interface SetUpStepPage {

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
    default void onReached(SetUpForm form) {
    }

    /**
     * A step that only shows its page.
     *
     * @param view the page, such as one of Settings'
     * @return the step's page
     */
    static SetUpStepPage of(Region view) {
        return of(view, _ -> { });
    }

    /**
     * A step that shows a page and acts as the owner reaches it.
     *
     * @param view the page, such as one of Settings'
     * @param reached what to do each time the step is shown, on the JavaFX thread
     * @return the step's page
     */
    static SetUpStepPage of(Region view, Consumer<SetUpForm> reached) {
        return new SetUpStepPage() {
            @Override
            public Region view() {
                return view;
            }

            @Override
            public void onReached(SetUpForm form) {
                reached.accept(form);
            }
        };
    }
}
