# Recorded answers

`open_meteo_3days.json` is a recorded answer of the Open-Meteo forecast API (https://open-meteo.com), used
by the tests so they never touch the network. Open-Meteo's data is licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/): Weather data by Open-Meteo.com. The only change
to the recording is that the coordinates in it are replaced by zeros; the tests never read them.
