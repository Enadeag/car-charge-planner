import QtQuick

// The dashboard as a window of its own (window mode, and the developer preview).
Window {
    id: window
    required property var bridge

    title: bridge.texts.title
    color: Theme.panel
    width: Theme.popupWidth
    height: Math.round(Theme.fontSize * 62)
    minimumWidth: Math.round(Theme.popupWidth * 0.75)

    Dashboard {
        anchors.fill: parent
        bridge: window.bridge
    }
}
