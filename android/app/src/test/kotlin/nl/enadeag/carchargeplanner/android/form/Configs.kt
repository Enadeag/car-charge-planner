package nl.enadeag.carchargeplanner.android.form

import java.time.ZoneId
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId

/** Set-up's draft for a home in Amsterdam's time zone, with Open-Meteo and every system value typical. */
internal val DRAFT: EngineConfig = EngineConfig.setUpDraft(ZoneId.of("Europe/Amsterdam"), ProviderId("open-meteo"))
