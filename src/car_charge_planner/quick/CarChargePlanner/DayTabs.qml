import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

// The day tabs of layout C: the day's short name over a dot in the colour of its source.
// Left and Right move between the tabs, as in a desktop tab bar; Tab leaves the bar.
TabBar {
    id: bar
    required property var days   // DaysModel
    property string accessibleName

    Accessible.name: accessibleName
    spacing: Theme.gapXs
    background: Item {}

    function step(by) {
        if (count === 0)
            return
        setCurrentIndex(Math.max(0, Math.min(count - 1, currentIndex + by)))
        currentItem.forceActiveFocus(Qt.TabFocusReason)
    }

    Repeater {
        model: bar.days

        TabButton {
            id: tabButton
            required property string tab
            required property string kind
            required property var day

            text: tab
            Accessible.name: day.title
            Accessible.description: day.summary
            font.pointSize: Theme.smallSize
            font.weight: Font.DemiBold
            implicitWidth: Math.max(implicitContentWidth + leftPadding + rightPadding, Theme.fontSize * 4.5)
            topPadding: Theme.gapS
            bottomPadding: Theme.gapS
            Keys.onLeftPressed: bar.step(-1)
            Keys.onRightPressed: bar.step(1)

            contentItem: ColumnLayout {
                spacing: Theme.gapXs
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: tabButton.text
                    font: tabButton.font
                    color: tabButton.checked ? Theme.fg : Theme.muted
                }
                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    implicitWidth: Theme.gapS
                    implicitHeight: Theme.gapS
                    radius: width / 2
                    color: Theme.source(tabButton.kind)
                }
            }
            background: Item {
                Rectangle {
                    anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
                    height: 3
                    color: tabButton.checked ? Theme.fg : "transparent"
                }
                FocusRing { shown: tabButton.visualFocus }
            }
        }
    }
}
