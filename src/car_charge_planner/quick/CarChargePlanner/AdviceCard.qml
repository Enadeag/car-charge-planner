import QtQuick
import QtQuick.Layouts

// Grid advice that needs acting on soon, at the top of today's tab.
Card {
    id: advice
    required property string chipText
    required property string title
    required property string detail
    required property string reason

    kind: "grid"
    soft: true
    Accessible.role: Accessible.Grouping
    Accessible.name: chipText

    Chip { text: advice.chipText; kind: "grid" }
    Text {
        Layout.fillWidth: true
        text: advice.title
        color: Theme.fg
        font.weight: Font.DemiBold
        wrapMode: Text.Wrap
    }
    Text {
        Layout.fillWidth: true
        text: advice.detail
        color: Theme.fg
        wrapMode: Text.Wrap
    }
    Text {
        Layout.fillWidth: true
        text: advice.reason
        color: Theme.muted
        font.pointSize: Theme.smallSize
        wrapMode: Text.Wrap
    }
}
