pragma Singleton
import QtQuick

// The design tokens: every colour, size and spacing the QML files use comes from here.
// Contrast (WCAG AA) is checked by tests/test_quick_theme.py: the *Text colours and fg/muted reach 4.5:1 on every
// surface and on their own soft background; the source colours and faint reach 3:1 as marks (lines, dots, bars).
QtObject {
    // follows the system; the preview and the tests may set it (python: quick.window.set_dark)
    property bool dark: Qt.styleHints.colorScheme === Qt.ColorScheme.Dark

    // surfaces
    readonly property color ground: dark ? "#0d1117" : "#e9edf2"
    readonly property color panel: dark ? "#161c24" : "#ffffff"
    readonly property color raised: dark ? "#1d2530" : "#f4f6f9"
    readonly property color line: dark ? "#2a3441" : "#dde3ea"   // dividers only: decoration, not a mark

    // text
    readonly property color fg: dark ? "#e8edf3" : "#17202b"
    readonly property color muted: dark ? "#9aa7b6" : "#566373"
    readonly property color faint: dark ? "#6c7a8a" : "#76849a"  // marks only (away dot, axis): too light for text

    // sources: the mark colour (chart lines, dots, bars), the text colour, the soft background behind a chip or card
    readonly property color sun: dark ? "#f2b33d" : "#b87300"
    readonly property color sunText: dark ? "#f2b33d" : "#8f5a00"
    readonly property color sunSoft: dark ? "#3a2c10" : "#fbe7c2"
    readonly property color sunArea: dark ? Qt.rgba(242 / 255, 179 / 255, 61 / 255, 0.26) : Qt.rgba(232 / 255, 160 / 255, 20 / 255, 0.28)
    readonly property color grid: dark ? "#6aa2f5" : "#2f6fd0"
    readonly property color gridText: dark ? "#6aa2f5" : "#2a62b8"
    readonly property color gridSoft: dark ? "#17283f" : "#dbe7fa"
    readonly property color car: dark ? "#4cc78c" : "#1f8f5c"
    readonly property color carText: dark ? "#4cc78c" : "#17734a"
    readonly property color carSoft: dark ? "#13301f" : "#d3f0e1"
    readonly property color act: dark ? "#f07575" : "#c93a3a"
    readonly property color actText: dark ? "#f07575" : "#b23030"
    readonly property color actSoft: dark ? "#3a1717" : "#f9dcdc"
    readonly property color focus: grid

    function source(kind) { return kind === "sun" ? sun : kind === "grid" ? grid : kind === "car" ? car : kind === "act" ? act : faint }
    function sourceText(kind) { return kind === "sun" ? sunText : kind === "grid" ? gridText : kind === "car" ? carText : kind === "act" ? actText : muted }
    function sourceSoft(kind) { return kind === "sun" ? sunSoft : kind === "grid" ? gridSoft : kind === "car" ? carSoft : kind === "act" ? actSoft : raised }

    // type: the system font, sizes relative to its point size, so the owner's font size and the screen scaling carry through
    readonly property font font: Qt.application.font
    readonly property real fontSize: Qt.application.font.pointSize > 0 ? Qt.application.font.pointSize : 10
    readonly property real smallSize: fontSize * 0.88
    readonly property real labelSize: fontSize * 0.8
    readonly property real titleSize: fontSize * 1.1
    readonly property real heroSize: fontSize * 2.1

    // spacing and shape, in device-independent pixels (Qt scales them for 150 and 200 %)
    readonly property int gapXs: 4
    readonly property int gapS: 8
    readonly property int gapM: 12
    readonly property int gapL: 16
    readonly property int gapXl: 24
    readonly property int radius: 14
    readonly property int radiusSmall: 8
    readonly property int focusWidth: 2
    readonly property int sourceBar: 4   // the coloured edge of a card or tab
    readonly property int popupWidth: 420
}
