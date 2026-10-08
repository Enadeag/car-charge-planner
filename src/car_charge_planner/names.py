"""Names of weekdays and months, translated like any other text.

They are translatable strings with a context (not looked up from the system) so that nothing here needs Qt
or a locale database, and a translator writes them once, the way their language abbreviates them. The
tuples are marked at import and translated when asked for.
"""

from __future__ import annotations

from datetime import date

from .i18n import NC_, _, translate

_WEEKDAYS_SHORT = (
    NC_("weekday, short", "Mon"),
    NC_("weekday, short", "Tue"),
    NC_("weekday, short", "Wed"),
    NC_("weekday, short", "Thu"),
    NC_("weekday, short", "Fri"),
    NC_("weekday, short", "Sat"),
    NC_("weekday, short", "Sun"),
)
_WEEKDAYS_LONG = (
    NC_("weekday", "Monday"),
    NC_("weekday", "Tuesday"),
    NC_("weekday", "Wednesday"),
    NC_("weekday", "Thursday"),
    NC_("weekday", "Friday"),
    NC_("weekday", "Saturday"),
    NC_("weekday", "Sunday"),
)
_MONTHS_SHORT = (
    NC_("month, short", "Jan"),
    NC_("month, short", "Feb"),
    NC_("month, short", "Mar"),
    NC_("month, short", "Apr"),
    NC_("month, short", "May"),
    NC_("month, short", "Jun"),
    NC_("month, short", "Jul"),
    NC_("month, short", "Aug"),
    NC_("month, short", "Sep"),
    NC_("month, short", "Oct"),
    NC_("month, short", "Nov"),
    NC_("month, short", "Dec"),
)


def weekday_short(index: int) -> str:
    """0 = Monday."""
    return translate(_WEEKDAYS_SHORT[index])


def weekday_long(index: int) -> str:
    """0 = Monday."""
    return translate(_WEEKDAYS_LONG[index])


def month_short(index: int) -> str:
    """0 = January."""
    return translate(_MONTHS_SHORT[index])


def weekdays_long() -> list[str]:
    return [weekday_long(i) for i in range(7)]


def short_date(day: date) -> str:
    """"Thu 8 Oct": the order of the three parts is the language's."""
    return _("{weekday} {day} {month}").format(
        weekday=weekday_short(day.weekday()), day=day.day, month=month_short(day.month - 1)
    )
