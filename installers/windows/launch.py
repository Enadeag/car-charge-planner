"""What the packaged program runs: the same entry point as the `car-charge-planner` command."""

import sys

from car_charge_planner.__main__ import main

sys.exit(main())
