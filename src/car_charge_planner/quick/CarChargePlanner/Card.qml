import QtQuick
import QtQuick.Layouts

// A raised card; with a `kind`, a coloured edge in that source's colour (and its soft background when `soft`).
Rectangle {
    id: card
    property string kind: ""
    property bool soft: false
    default property alias content: column.data

    color: soft && kind !== "" ? Theme.sourceSoft(kind) : Theme.raised
    radius: Theme.radius
    implicitHeight: column.implicitHeight + 2 * Theme.gapM
    implicitWidth: column.implicitWidth + 2 * Theme.gapM

    Rectangle {
        visible: card.kind !== ""
        width: Theme.sourceBar
        color: Theme.source(card.kind)
        anchors { left: parent.left; top: parent.top; bottom: parent.bottom; topMargin: Theme.radius / 2; bottomMargin: Theme.radius / 2 }
    }
    ColumnLayout {
        id: column
        anchors.fill: parent
        anchors.margins: Theme.gapM
        anchors.leftMargin: Theme.gapM + (card.kind !== "" ? Theme.sourceBar : 0)
        spacing: Theme.gapS
    }
}
