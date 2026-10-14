import QtQuick
import QtQuick.Controls.Basic

Button {
    id: control
    property string hint: ""

    Accessible.name: text
    Accessible.description: hint
    font.pointSize: Theme.smallSize
    font.weight: Font.DemiBold
    topPadding: Theme.gapXs + 1
    bottomPadding: Theme.gapXs + 1
    leftPadding: Theme.gapM
    rightPadding: Theme.gapM
    ToolTip.visible: hint !== "" && hovered
    ToolTip.text: hint
    ToolTip.delay: 600

    contentItem: Text {
        text: control.text
        font: control.font
        color: Theme.fg
        horizontalAlignment: Text.AlignHCenter
        verticalAlignment: Text.AlignVCenter
    }
    background: Rectangle {
        radius: Theme.radiusSmall
        color: control.down ? Theme.line : Theme.raised
        border.color: control.hovered ? Theme.faint : Theme.line
        FocusRing { shown: control.visualFocus }
    }
}
