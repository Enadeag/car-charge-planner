# Answers of the route service

The tests use these answers so they never touch the network. None of them is a recording: they are made by hand in
the shape Geoapify documents (https://apidocs.geoapify.com/docs/geocoding/forward-geocoding/ for the search,
https://apidocs.geoapify.com/docs/routing/ for the route, and its error answers of `statusCode`, `error` and
`message`), for the made-up places Zonnedorp (the home) and Zeedorp, at made-up coordinates in the made-up country
Nowhere.

- `geoapify_search_one.json`: a search that finds one place.
- `geoapify_search_several.json`: a search that finds three places of the same name, the one closest to home second.
- `geoapify_search_none.json`: a search that finds nothing.
- `geoapify_route.json`: a route by car, with its line shortened to three points.
- `geoapify_route_none.json`: a route that cannot be made; Geoapify does not document this answer, only that such a
  request fails with a status from 400 to 499.
- `geoapify_401.json` and `geoapify_429.json`: a refused API key and too many requests.
