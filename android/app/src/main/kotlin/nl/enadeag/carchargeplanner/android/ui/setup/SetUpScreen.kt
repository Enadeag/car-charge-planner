package nl.enadeag.carchargeplanner.android.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.setup.SetUpStep
import nl.enadeag.carchargeplanner.android.ui.components.Page
import nl.enadeag.carchargeplanner.android.ui.components.ProblemFocus
import nl.enadeag.carchargeplanner.android.ui.components.AppButton
import nl.enadeag.carchargeplanner.android.ui.components.AppOutlinedButton

/**
 * Counts the owner's edits on set-up's screen. The forms keep their texts in the saved state, which Compose does not
 * watch, and every change to them comes from the owner on this screen: each one counts here, and what a step reads
 * through [shown] is shown anew after each.
 */
@Stable
internal class Edits {

    private var count by mutableIntStateOf(0)

    /**
     * Reads what the forms hold, so the part of the screen that shows it is shown anew after each edit.
     *
     * @param T what is read
     * @param read reads the forms
     * @return what they hold now
     */
    fun <T> shown(read: () -> T): T = count.let { read() }

    /**
     * Makes an edit and counts it.
     *
     * @param edit changes the forms
     */
    fun make(edit: () -> Unit) {
        edit()
        count++
    }
}

/**
 * Set-up's screen: the step's heading with "step 3 of 7", the step itself, and Back and Next, or Finish on the last
 * step. Back on the welcome leaves at once; on a later step it goes one step back. A new step moves the focus to its
 * heading; a Next or Finish that shows problems moves it to the first field that has one.
 *
 * @param session set-up's steps, forms and Finish
 */
@Composable
internal fun SetUpScreen(session: SetUpSession) {
    val step by session.flow.step.collectAsStateWithLifecycle()
    val refused by session.refused.collectAsStateWithLifecycle()
    val problemsShown by session.problemsShown.collectAsStateWithLifecycle()
    val edits = remember { Edits() }
    val heading = remember { FocusRequester() }
    val problems = remember(step) { ProblemFocus() }
    var seenStep by rememberSaveable { mutableStateOf<SetUpStep?>(null) }
    var seenProblems by rememberSaveable { mutableIntStateOf(problemsShown) }

    BackHandler(enabled = step != SetUpStep.WELCOME) { session.back() }
    LaunchedEffect(step, problemsShown) {
        // the fields are noted once the step is composed, which this waits for
        withFrameNanos { }
        if (problemsShown != seenProblems) {
            seenProblems = problemsShown
            seenStep = step
            if (!problems.focusFirst()) {
                heading.requestFocus()
            }
        } else if (seenStep != step) {
            seenStep = step
            heading.requestFocus()
        }
    }

    Page {
        StepHeading(step, heading)
        if (refused) {
            Text(stringResource(R.string.setup_refused), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        when (step) {
            SetUpStep.WELCOME -> WelcomeStep(session.providerName())
            SetUpStep.LOCATION -> LocationStep(session, edits, problems)
            SetUpStep.PANELS -> PanelsStep(session.flow.panels, edits, problems)
            SetUpStep.CAR -> CarStep(session.flow.car, edits, problems)
            SetUpStep.CHARGER -> ChargerStep(session.flow.charger, edits, problems)
            SetUpStep.MORE -> MoreStep(session.flow.house, session.flow.away, edits, problems)
            SetUpStep.CHECK -> CheckStep(session)
        }
        StepButtons(session, step)
    }
}

/** The step's heading with its place among the steps, read as one heading. */
@Composable
private fun StepHeading(step: SetUpStep, focus: FocusRequester) {
    Column(Modifier.focusRequester(focus).focusable().semantics(mergeDescendants = true) { heading() }) {
        Text(stringResource(R.string.setup_step_of, step.number(), SetUpStep.entries.size),
            style = MaterialTheme.typography.labelLarge)
        Text(stringResource(step.title()), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Back and Next, or Back and Finish, which waits while it saves. */
@Composable
private fun StepButtons(session: SetUpSession, step: SetUpStep) {
    val finishing by session.finishing.collectAsStateWithLifecycle()
    val saving = finishing == SetUpSession.Finishing.Saving
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        if (step != SetUpStep.WELCOME) {
            AppOutlinedButton(onClick = { session.back() }, enabled = !saving) {
                Text(stringResource(R.string.action_back))
            }
        }
        if (step == SetUpStep.CHECK) {
            AppButton(onClick = { session.finish() }, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.finish_saving), modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.action_finish))
                }
            }
        } else {
            AppButton(onClick = { session.next() }) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}

/**
 * The step's title.
 *
 * @return the text's resource
 */
private fun SetUpStep.title(): Int = when (this) {
    SetUpStep.WELCOME -> R.string.setup_welcome_title
    SetUpStep.LOCATION -> R.string.setup_location_title
    SetUpStep.PANELS -> R.string.setup_panels_title
    SetUpStep.CAR -> R.string.setup_car_title
    SetUpStep.CHARGER -> R.string.setup_charger_title
    SetUpStep.MORE -> R.string.setup_more_title
    SetUpStep.CHECK -> R.string.setup_check_title
}
