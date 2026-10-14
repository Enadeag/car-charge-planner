import QtQuick
import QtQuick.Layouts

// The day's main card: its charge window, or why there is none and the grid charge, or the time away.
Card {
    id: main
    required property var day

    kind: day.kind === "sun" || day.kind === "grid" ? day.kind : ""
    soft: kind !== ""
    Accessible.role: Accessible.Grouping
    Accessible.name: day.heading
    Accessible.description: day.summary

    RowLayout {
        Layout.fillWidth: true
        spacing: Theme.gapS
        Text {
            text: main.day.title
            color: Theme.fg
            font.pointSize: Theme.smallSize
            font.weight: Font.DemiBold
        }
        Text {
            Layout.fillWidth: true
            text: main.day.heading
            color: Theme.muted
            font.pointSize: Theme.smallSize
            wrapMode: Text.Wrap
        }
    }
    Text {
        visible: text !== ""
        Layout.fillWidth: true
        text: main.day.hero
        color: Theme.fg
        font.pointSize: Theme.heroSize
        font.weight: Font.DemiBold
        font.features: { "tnum": 1 }
        wrapMode: Text.Wrap
    }
    Flow {
        visible: main.day.facts.length > 0
        Layout.fillWidth: true
        spacing: Theme.gapM
        Repeater {
            model: main.day.facts
            Text {
                required property string modelData
                text: modelData
                color: Theme.fg
                font.pointSize: Theme.smallSize
            }
        }
    }
    Text {
        visible: text !== ""
        Layout.fillWidth: true
        text: main.day.note
        color: Theme.muted
        font.pointSize: Theme.smallSize
        wrapMode: Text.Wrap
    }
}
