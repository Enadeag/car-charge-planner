import QtQuick
import QtQuick.Layouts

// The battery before and after the day's charging, with the minimum and target marked.
ColumnLayout {
    id: battery
    required property var day
    required property string heading

    spacing: Theme.gapS
    Accessible.role: Accessible.Graphic
    Accessible.name: day.batterySummary

    RowLayout {
        Layout.fillWidth: true
        Text {
            Layout.fillWidth: true
            text: battery.heading
            color: Theme.muted
            font.pointSize: Theme.labelSize
            font.weight: Font.DemiBold
            font.capitalization: Font.AllUppercase
        }
        Text {
            text: battery.day.batteryText
            color: Theme.carText
            font.pointSize: Theme.smallSize
            font.weight: Font.DemiBold
            font.features: { "tnum": 1 }
        }
    }
    Item {
        id: track
        Layout.fillWidth: true
        implicitHeight: Theme.gapM + minimumLabel.implicitHeight + Theme.gapXs

        function at(pct) { return width * Math.max(0, Math.min(100, pct)) / 100 }

        Rectangle {
            id: bar
            width: parent.width
            height: Theme.gapM
            radius: height / 2
            color: Theme.line
            Rectangle {
                width: track.at(battery.day.batteryAfter)
                height: parent.height
                radius: parent.radius
                color: Theme.car
                opacity: 0.35
            }
            Rectangle {
                width: track.at(battery.day.batteryBefore)
                height: parent.height
                radius: parent.radius
                color: Theme.car
            }
        }
        Repeater {
            model: [
                { pct: battery.day.minimum, text: battery.day.minimumText },
                { pct: battery.day.target, text: battery.day.targetText }
            ]
            Item {
                id: mark
                required property var modelData
                x: track.at(modelData.pct)
                Rectangle { x: -1; y: -Theme.gapXs / 2; width: 2; height: bar.height + Theme.gapXs; color: Theme.fg; opacity: 0.6 }
                Text {
                    id: markLabel
                    x: Math.max(-mark.x, Math.min(track.width - mark.x - width, -width / 2))
                    y: bar.height + Theme.gapXs
                    text: mark.modelData.text
                    color: Theme.muted
                    font.pointSize: Theme.labelSize
                }
            }
        }
        Text { id: minimumLabel; visible: false; text: battery.day.minimumText; font.pointSize: Theme.labelSize }
    }
}
