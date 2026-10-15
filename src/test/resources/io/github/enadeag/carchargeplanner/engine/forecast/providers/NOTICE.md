# Recorded answers

The tests use these answers so they never touch the network.

`open_meteo_3days.json` is a recorded answer of the Open-Meteo forecast API (https://open-meteo.com). Open-Meteo's
data is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/): Weather data by Open-Meteo.com. The
only change to the recording is that the coordinates in it are replaced by zeros; the tests never read them.

`open_meteo_geocoding_amsterdam.json` and `open_meteo_geocoding_none.json` are recorded answers of Open-Meteo's
geocoding API, for "Amsterdam" and for a name that matches nothing. Place data: GeoNames (CC BY 4.0), via
Open-Meteo.com.

`forecast_solar_2days.json` is a recorded answer of the Forecast.Solar public API (https://forecast.solar,
`estimate/watthours/period`) for a neutral location (the centre of Amsterdam). Only the place name, the request id and
the requester's address in the rate-limit zone were replaced. Credit: Solar forecast by Forecast.Solar.

`forecast_solar_429.json`, `forecast_solar_dst_spring.json` and `forecast_solar_dst_autumn.json` are **not
recordings**: they are made by hand in the documented shape (the rate-limit answer from the "Facing 429 responses"
page; the two daylight saving days to exercise the time handling around the missing and the repeated hour).
