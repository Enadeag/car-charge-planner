package nl.enadeag.carchargeplanner.engine.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OnlineLocationsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Microsoft Teams Meeting",
            "Microsoft Teams-vergadering",
            "Room 3; Microsoft Teams Meeting",
            "https://teams.microsoft.com/l/meetup-join/19%3ameeting",
            "https://zoom.us/j/123456789?pwd=abc",
            "zoom.us/j/123456789",
            "Zoom",
            "meet.google.com/abc-defg-hij",
            "Google Meet",
            "www.zeedorp.example",
            "mailto:someone@example.org",
            "tel:+31000000000",
            "Online",
            "ONLINE via Webex",
            "Skype",
            "Telefonisch",
            "Video call",
            "Conference   call"})
    void aLinkOrAMeetingWithoutAPlaceIsOnline(String text) {
        assertThat(OnlineLocations.isOnline(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Havenkade 7, Zeedorp",
            "Zonnedorp",
            "Zoomweg 3, Zeedorp",
            "Teamsportcentrum Zeedorp",
            "Meeting room 3",
            "Callaweg 12, Zonnedorp",
            "Café De Ster, Markt 1, Zeedorp",
            ""})
    void aPlaceOrNothingIsNotOnline(String text) {
        assertThat(OnlineLocations.isOnline(text)).isFalse();
    }
}
