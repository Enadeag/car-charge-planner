import QtQuick

// A short label on a soft background in a source's colours, for example "88% sun".
Rectangle {
    id: chip
    property string text
    property string kind: ""

    Accessible.role: Accessible.StaticText
    Accessible.name: text
    implicitWidth: label.implicitWidth + 2 * Theme.gapS
    implicitHeight: label.implicitHeight + Theme.gapXs
    radius: height / 2
    color: Theme.sourceSoft(kind)

    Text {
        id: label
        anchors.centerIn: parent
        text: chip.text
        color: Theme.sourceText(chip.kind)
        font.pointSize: Theme.smallSize
        font.weight: Font.DemiBold
    }
}
