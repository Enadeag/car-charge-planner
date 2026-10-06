# Car Charge Notification

A personal tool that tells the owner each day when to charge their electric car so that as much of their own solar energy as possible is used.

## Language

**Feed-in**:
Solar energy that is produced but not used in the home or car, and flows back to the grid. The thing the owner wants to minimise.
_Avoid_: Return, teruglevering, export

**Charge window**:
A contiguous period of the day that the tool recommends for charging the car, chosen to capture the most solar surplus.
_Avoid_: Slot, charge time

**Charge power**:
The charging rate (kW) the tool recommends for a charge window, chosen to match the expected solar surplus. The owner sets it in the charger's app.
_Avoid_: Charge speed, charge rate, current

**Surplus**:
Solar production minus the house's own use at a given moment; what is left over for the car (or would be fed in).
_Avoid_: Excess, spare solar

**Away period**:
A span of time when the car is not at home and so cannot be charged. Recurring (Thursdays 06:00–19:00) or entered by hand for a specific day.
_Avoid_: Absence, trip

**Minimum charge level**:
The battery percentage the car must never fall below (30%). The plan must keep the car above it, even if that means charging outside the best solar window.
_Avoid_: Floor, reserve

**Self-consumption**:
The share of produced solar energy that is used on-site (home or car) rather than fed in. The primary quantity the tool maximises.
_Avoid_: Own use, direct use
