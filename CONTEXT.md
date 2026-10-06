# Car Charge Planner

A tool that tells the user each day when to charge their electric car so that as much of their own solar energy as possible is used.

## Language

**Feed-in**:
Solar energy that is produced but not used in the home or car, and flows back to the grid. The thing the user wants to minimise.
_Avoid_: Return, teruglevering, export

**Charge window**:
A contiguous period of the day that the tool recommends for charging the car, chosen to capture the most solar surplus.
_Avoid_: Slot, charge time

**Charge power**:
The charging rate (kW) the tool recommends for a charge window, chosen to match the expected solar surplus. The user sets it in the charger's app.
_Avoid_: Charge speed, charge rate, current

**Surplus**:
Solar production minus the house's own use at a given moment; what is left over for the car (or would be fed in).
_Avoid_: Excess, spare solar

**Away period**:
A span of time when the car is not at home and so cannot be charged. Recurring (for example Thursdays 06:00–19:00) or entered by hand for a specific day.
_Avoid_: Absence, trip

**Minimum charge level**:
The battery percentage the car must never fall below (30% unless the user changes it). The plan must keep the car above it, even if that means charging outside the best solar window.
_Avoid_: Floor, reserve

**Self-consumption**:
The share of produced solar energy that is used on-site (home or car) rather than fed in. The primary quantity the tool maximises.
_Avoid_: Own use, direct use

## Setting up

**Set-up**:
The guided first-run process in which the user describes their system (location, panels, car, charger, and optionally house and away times) so a plan can be made. Short for what the user does once; the ongoing editing of values is Settings.
_Avoid_: Onboarding, configuration, wizard (the wizard is only the form set-up takes)

**Not set up**:
The state before a real location has been saved: no forecast is fetched and no plan is shown, only a way into set-up.
_Avoid_: Unconfigured, first run (first run is the moment, not the state)

**Typical value**:
A setting the user did not give ("I don't know") and the app filled in with a common value. It is flagged as an estimate until the user saves a value of their own.
_Avoid_: Default, assumed value, guess

**Charger phases**:
Whether the charger charges on one phase or three. Each amp delivers three times the power on three phases, and so does the minimum current.
_Avoid_: Single/three-phase connection (the home connection can have three phases while the charger uses one)
