"""Build hook: compile the translations (.po -> .mo) so the wheel carries what the app loads at run time.

The compiled files are not kept in the repository. This runs for every build, editable installs included, and needs
Babel (a build requirement).
"""

import sys
from pathlib import Path

from hatchling.builders.hooks.plugin.interface import BuildHookInterface


class CompileTranslations(BuildHookInterface):
    PLUGIN_NAME = "custom"

    def initialize(self, version, build_data):
        source = Path(self.root) / "src"
        locale = source / "car_charge_planner" / "locale"
        if not any(locale.glob("*/LC_MESSAGES/*.po")):
            return  # English is the source language: with no translation there is nothing to compile
        sys.path.insert(0, str(source))
        from car_charge_planner.i18n_tools import compile_catalogues

        compile_catalogues(locale)
