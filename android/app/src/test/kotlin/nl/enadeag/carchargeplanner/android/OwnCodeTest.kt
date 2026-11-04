package nl.enadeag.carchargeplanner.android

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** Which stacks of a main-thread violation run through the app's own code, innermost frame first. */
class OwnCodeTest {

    private companion object {

        /** The stack of the disk read in a phone's input code that ended the debug build on the first key. */
        val VENDOR = listOf(
            "java.io.File.exists",
            "android.os.FactoryTest.isFactoryApk",
            "android.os.FactoryTest.isFactoryMode",
            "com.samsung.android.rune.InputRune.<clinit>",
            "android.view.ViewRootImpl\$ViewPostImeInputStage.processKeyEvent",
            "android.os.Looper.loop",
            "android.app.ActivityThread.main",
        )

        /** Compose and coroutines frames between the read and the code that started it. */
        val LIBRARIES = listOf(
            "java.io.File.exists",
            "kotlinx.coroutines.DispatchedTask.run",
            "androidx.compose.runtime.Recomposer.runRecomposeAndApplyChanges",
            "androidx.compose.ui.platform.AndroidUiDispatcher.performTrampolineDispatch",
        )

        fun stack(vararg methods: String): Array<StackTraceElement> = methods.map {
            StackTraceElement(it.substringBeforeLast('.'), it.substringAfterLast('.'), null, -1)
        }.toTypedArray()

        @JvmStatic
        fun stacks(): List<Arguments> = listOf(
            Arguments.of("only the system's and a vendor's code", stack(*VENDOR.toTypedArray()), false),
            Arguments.of(
                "the app's own code at the bottom, under the system's",
                stack(*VENDOR.toTypedArray(), "nl.enadeag.carchargeplanner.android.MainActivity.onCreate"),
                true,
            ),
            Arguments.of(
                "a lambda of the app's own under Compose and coroutines",
                stack(
                    *LIBRARIES.toTypedArray(),
                    "nl.enadeag.carchargeplanner.android.ui.HomeScreenKt\$HomeScreen\$1\$1.invokeSuspend",
                    "android.os.Looper.loop",
                ),
                true,
            ),
            Arguments.of(
                "the engine's code",
                stack("java.io.File.exists", "nl.enadeag.carchargeplanner.engine.config.ConfigFile.read"),
                true,
            ),
            Arguments.of(
                "a package that only starts like the app's, and the application ID",
                stack(
                    *VENDOR.toTypedArray(),
                    "nl.enadeag.carchargeplanner2.Other.run",
                    "io.github.enadeag.car_charge_planner.Some.run",
                ),
                false,
            ),
            Arguments.of("no frames", stack(), false),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stacks")
    fun aStackIsTheAppsOwnWhenAnyFrameIsInItsPackages(
        case: String,
        stack: Array<StackTraceElement>,
        own: Boolean,
    ) {
        assertThat(OwnCode.isIn(stack)).describedAs(case).isEqualTo(own)
    }
}
