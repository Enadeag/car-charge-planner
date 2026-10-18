# Integrations are built into the app

After the public launch the app reads devices: the car's battery level, the inverter's production, the smart meter's house use and grid flow, and later a charger. Each of those is an **integration**, and an integration is code in this repository: it comes in by pull request, is reviewed, and is released with the app. There are no code plugins, and declarative "recipes" that users could add without a release are deferred (below), not rejected. The forecast providers showed the pattern (a contract, an injected transport, a conformance kit, one service for retries and cool-downs, [docs/providers.md](../providers.md)); #11 generalises it into a shared core that every integration uses.

The reasons: the services differ most where it matters, in how they sign in (API keys, OAuth with PKCE, device flows, developer apps of the owner's own, passwords), so a declarative format would either grow into a programming language or fall short for most of them; the built-in core is needed anyway; and recipes can be layered on top of it later without undoing anything.

## The decision

- **Built in, by pull request.** Every integration is Java code in the engine, reviewed like any other change and shipped in a release. Nothing is loaded from other packages or downloaded at run time.
- **Control only by built-in code under #16's rules.** Reading a device and controlling it are different risks. Setting a charger's current, or starting and stopping a charge, is only ever done by built-in code that meets the rules #16 decides.
- **Home Assistant is one optional integration.** It is never the basis of the design and never a fallback route for a device the app does not support. The app must work fully without Home Assistant; a Home Assistant integration may be added like any other.
- **Plain http only to the local network.** An address may use plain http only when it points to a private address: RFC 1918 (10/8, 172.16/12, 192.168/16), link-local (169.254/16, fe80::/10), unique local IPv6 (fc00::/7), or a `*.local` name. The check is made on the resolved IP address when connecting, so a name that resolves to a public address is refused. Everything else uses https. Settings labels such an integration "Local network, not encrypted". (Today's `ServerAddress` allows plain http only to localhost; this widens it.)
- **Accounts that only work with a password.** A cloud service that offers no API key or OAuth may be built in, with safeguards: the password is sent only to the service's own login host, and where the service allows it only the refresh token is kept, not the password. Reverse-engineered password APIs of car makers are not used: they break without notice, are often against the maker's terms, and are sometimes blocked on purpose.
- **Cars only through official owner routes** (the table below). First Škoda (public API, an API key the owner creates in the MyŠkoda app) and BMW/Mini (CarData, a client id and a device flow with PKCE); then Hyundai/Kia (Pleos), Volvo/Polestar and Ford, each with a developer app of the owner's own; Tesla only if someone contributes it. Entering the battery level by hand always remains possible. Brands with no official route are listed in the README as having none. Aggregators (Smartcar, a local evcc) may come later as extra options, and ISO 15118 (the car telling the charger its battery level) belongs with the charger work.
- **Order of work, after the launch:** the shared core (#11); the abilities (#12); the P1 smart meter as the reference integration (house use and grid flow, local, no account); the inverter through SunSpec over Modbus TCP (which brings the Modbus transport of #13); then chargers, reading first (control waits for #16). Calendar sync for away periods (#36) runs alongside, as it gives away periods rather than readings and does not wait for #11 or #12. Cars through official routes come after these. The stand-alone engine (#60) comes later, on its own.

## Validation

No maintainer owns every car, meter or inverter, so an integration cannot wait for the maintainer to test it on the real thing.

- An integration is built and tested against documented or recorded responses: its conformance kit, as the forecast providers have.
- It ships as **Untested** until an owner confirms it on a real device, which makes it **Beta**. It is **Supported** only while someone checks it on every release. These are the same three words as the systems table in the README.
- The **Integration report** issue form asks owners to check one and report; such issues get the `help wanted` label.

## Considered options

- **Built in (chosen):** one codebase, one review, one release; every integration uses the same transport, secret store, error handling and Settings page; the conformance kit tests it without a device. Costs a release for every new integration and every fix, and the maintainer reviews all of them.
- **Code plugins** (jars loaded at run time): third parties ship without a release, but their code runs with the app's rights and the owner's keys, the jlink'd image would need a module path for them, and the contract between app and plugin becomes a public API to keep stable. #11 had already ruled them out.
- **Separate processes** (an integration as its own program talking to the app over a local protocol): isolates failures and allows any language, but adds a protocol, a process life cycle and an install step per integration, for a desktop app that is meant to be one program (ADR 0001).
- **Recipes** (declarative JSON that describes a request and where the value is in the answer): no code to review and no release needed. Deferred; see below.
- **Leaning on an ecosystem** (Home Assistant, evcc, or an aggregator such as Smartcar as the one route to all devices): one adapter would reach many devices, but only for people who run that system or have that account, and the app must work without them. Each may still become one optional integration.

## The paper test

Before deciding, a small recipe language (one request, a JSONPath to the value, a unit) was tried on paper against real services:

| Service | Fits the small language? | What it would need |
| --- | --- | --- |
| SolarEdge monitoring (site overview) | Yes | An API key |
| HomeWizard P1 meter, local API v1 | Yes | Plain http on the local network |
| A Home Assistant entity | Yes | Plain http on the local network, a long-lived token |
| Easee charger (cloud) | With one more block | A password login that returns a token; a map from status codes to states, amps as the unit, JSONPath filters |
| Forecast.Solar | No, about two more blocks | One request per panel array and adding them up; energy since the previous time step instead of power |
| Open-Meteo | No | Turning irradiance into panel output (physics), one request per array, answers in columns |
| Tesla (Fleet API) | No, and not because of the language | A client secret, partner registration on a domain, a paid account |

Recipes would pay off for simple live readings (a meter, an inverter, a charger's power, a Home Assistant entity). Forecasts need code. Cars are held back by the makers' terms and sign-in flows, not by any format.

## Car routes

Researched in October 2026, mainly from evcc's vehicle documentation and evcc's discussion of the EU Data Act, and the makers' own pages.

| Brand | Official owner route | What the owner needs | Limits | Source |
| --- | --- | --- | --- | --- |
| Škoda | Public API | An API key created in the MyŠkoda app, sent as `X-API-Key` | Free, 20 requests an hour per car, personal use only | [Škoda API keys](https://go.skoda.eu/api-keys), [evcc](https://docs.evcc.io/en/vehicles/skoda-myskoda-public-api) |
| BMW, Mini | CarData | A client id from the BMW or Mini portal; device flow with PKCE | Free for personal use, about 50 REST calls a day, plus an MQTT stream | [CarData API](https://bmw-cardata.bmwgroup.com/customer/public/api-documentation), [evcc](https://docs.evcc.io/en/vehicles/bmw-cardata) |
| Hyundai, Kia | Pleos vehicle data API | A client id and secret of the owner's own | | [Pleos](https://document.pleos.ai/en/api-reference/vehicle-data-api/intro) |
| Volvo | Energy API v2 | A developer app of the owner's own, approved by hand | Signing in again every 7 days | [Volvo developers](https://developer.volvocars.com/), [evcc](https://docs.evcc.io/en/vehicles/volvo) |
| Polestar | Data Portal | A client id, secret and account id of the owner's own | | [Polestar Data Portal](https://data-portal.polestar.com/), [evcc](https://docs.evcc.io/en/vehicles/polestar) |
| Ford | FordConnect Query | A developer app of the owner's own; a redirect to localhost is allowed | | [Ford developers](https://developer.ford.com/developer-eu), [evcc](https://docs.evcc.io/en/vehicles/ford-fordconnect-query) |
| Tesla | Fleet API | A developer account of the owner's own, a key published on a domain, a payment card | A $10 monthly credit for personal use | [Tesla developers](https://developer.tesla.com/), [evcc](https://docs.evcc.io/en/vehicles/tesla) |
| Volkswagen, Audi, Cupra | None usable | | The EU Data Act portal gives a file to download, about 15 minutes late and unreliable | [evcc discussion 23684](https://github.com/evcc-io/evcc/discussions/23684) |
| Mercedes, Stellantis brands, Renault, Toyota, MG, BYD, Nissan | None | | Only reverse-engineered password APIs | [evcc discussion 23684](https://github.com/evcc-io/evcc/discussions/23684), [evcc vehicles](https://docs.evcc.io/en/vehicles) |
| Aggregators | Smartcar | An account of the owner's own and a client secret | Free for one car; Enode and High Mobility are business-to-business only | [Smartcar](https://smartcar.com/) |

## Recipes, deferred

The design was worked out and is kept here, so it can be picked up as it is:

- **A catalogue.** Recipes live in a separate catalogue repository and are added there by pull request, with a light review (by the maintainer until there are co-maintainers). Settings shows the catalogue; an owner can also import a recipe file by hand, which is labelled "not from the catalogue". No other catalogue addresses for now.
- **Read only.** A recipe only reads; it never controls a device. "Read only" means no actions, not "GET only": a POST that asks a question is allowed. The language stays small: no conditions, no loops, and no arithmetic beyond converting units. What does not fit becomes built-in code.
- **Updates with consent.** Catalogue recipes update automatically, but the app asks the owner first when an update sends to a different host or sends more or other data (it compares the hosts and which settings go where). A recipe imported by hand never updates itself.
- **No account passwords.** A recipe gets only API keys or tokens the owner created, never an account password.
- **JSON with a JSON Schema**, so no new parser is needed; a `notes` field takes the place of comments.
- **Licence:** CC BY 4.0 for catalogue recipes, agreed to by contributors in the catalogue's CONTRIBUTING.
- **A language version** in every recipe (`"recipe": 1`); the app refuses a newer version than it knows, as it does with its own file versions.

**Reconsider** when many requests come in for simple single-value live readings (a meter, an inverter, a charger's power) that the shared core almost covers already, so that a recipe would replace a pull request of a few lines.

## Consequences

- Each new device, and each fix to one, waits for a release. That is the price of review and of one place for keys, errors and Settings.
- The shared core (#11) and the abilities (#12) come before any device; the P1 meter proves them, and the SunSpec inverter brings Modbus (#13).
- `ServerAddress` changes: plain http to private addresses, checked on the resolved address, with the Settings label.
- An integration's status (Untested, Beta, Supported) is shown in the README beside it, and integration reports are how it moves up.
- Brands without an official route are not built, however often they are asked for; an issue for one is closed as not planned until the maker offers a route. Entering the battery level by hand stays.
- The app keeps working, fully, for someone without Home Assistant, evcc or an aggregator account.
