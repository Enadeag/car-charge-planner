package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import java.util.List;

/** Solar sites for the tests, made from others. */
public final class SolarSites {

    private SolarSites() {
    }

    /** The same place with other panel arrays, such as one array of several. */
    public static SolarSite withArrays(SolarSite site, List<PanelArray> arrays) {
        return new SolarSite(site.location(), site.timezone(), arrays);
    }
}
