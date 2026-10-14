import QtQuick

// The visible keyboard focus: put one in a control's background with `shown: control.visualFocus`.
Rectangle {
    property bool shown: false
    objectName: "focusRing"
    anchors.fill: parent
    anchors.margins: -Theme.focusWidth
    radius: Theme.radiusSmall + Theme.focusWidth
    color: "transparent"
    border.width: Theme.focusWidth
    border.color: Theme.focus
    visible: shown
}
