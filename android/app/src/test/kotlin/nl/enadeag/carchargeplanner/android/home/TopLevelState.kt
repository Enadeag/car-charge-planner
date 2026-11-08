package nl.enadeag.carchargeplanner.android.home

import kotlinx.coroutines.sync.Mutex

/** Kept across the process, which `AppArchitectureTest`'s rule on static state refuses. */
internal var counted = 0

/** A lock for the whole process, which the rule refuses too. */
internal val ONE_AT_A_TIME = Mutex()

/** A list for the whole process that changes, which the rule refuses too. */
internal val seen = ArrayList<String>()

/** A list for the whole process that changes, typed as Kotlin's interface, which the rule refuses too. */
internal val asked: MutableList<String> = mutableListOf()

/** A map for the whole process that changes, made with values, which the rule refuses too. */
internal val counts: MutableMap<String, Int> = mutableMapOf("rounds" to 0)

/** A list that cannot change, which the rule lets through. */
internal val names = listOf("Open-Meteo", "Forecast.Solar")

/** A constant, which the rule lets through. */
internal const val LIMIT = 3
