import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts

// Layout C, browse by day: the day tabs, then the selected day (grid advice on today, its main card, its solar curve,
// its battery), then the footer. Keyboard order follows the layout: tabs, then the footer's buttons.
Rectangle {
    id: dashboard
    required property var bridge
    readonly property var day: tabs.currentItem ? tabs.currentItem.day : null

    color: Theme.panel
    implicitWidth: Theme.popupWidth
    implicitHeight: column.implicitHeight

    ColumnLayout {
        id: column
        anchors.fill: parent
        spacing: 0

        Text {
            Layout.fillWidth: true
            Layout.margins: Theme.gapL
            Layout.bottomMargin: Theme.gapS
            text: dashboard.bridge.texts.title
            color: Theme.fg
            font.pointSize: Theme.titleSize
            font.weight: Font.DemiBold
            Accessible.role: Accessible.Heading
            Accessible.name: text
        }

        DayTabs {
            id: tabs
            visible: count > 0
            Layout.fillWidth: true
            Layout.leftMargin: Theme.gapM
            Layout.rightMargin: Theme.gapM
            days: dashboard.bridge.days
            accessibleName: dashboard.bridge.texts.days
            focus: true
        }
        Rectangle { Layout.fillWidth: true; implicitHeight: 1; color: Theme.line }

        ScrollView {
            id: scroll
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            clip: true

            ColumnLayout {
                width: scroll.availableWidth
                spacing: Theme.gapM

                Item { implicitHeight: Theme.gapXs }
                AdviceCard {
                    visible: dashboard.bridge.hasAdvice && tabs.currentIndex === 0
                    Layout.fillWidth: true
                    Layout.leftMargin: Theme.gapL
                    Layout.rightMargin: Theme.gapL
                    chipText: dashboard.bridge.texts.gridAdvice
                    title: dashboard.bridge.adviceTitle
                    detail: dashboard.bridge.adviceDetail
                    reason: dashboard.bridge.adviceReason
                }
                Card {
                    // no plan (not set up, no forecast): the view model's headline and detail
                    visible: dashboard.day === null
                    Layout.fillWidth: true
                    Layout.leftMargin: Theme.gapL
                    Layout.rightMargin: Theme.gapL
                    Text { Layout.fillWidth: true; text: dashboard.bridge.headline; color: Theme.fg; font.pointSize: Theme.titleSize; font.weight: Font.DemiBold; wrapMode: Text.Wrap }
                    Text { Layout.fillWidth: true; text: dashboard.bridge.detail; color: Theme.muted; wrapMode: Text.Wrap }
                }
                Loader {
                    active: dashboard.day !== null
                    visible: active
                    Layout.fillWidth: true
                    Layout.leftMargin: Theme.gapL
                    Layout.rightMargin: Theme.gapL
                    sourceComponent: ColumnLayout {
                        spacing: Theme.gapM
                        MainCard { Layout.fillWidth: true; day: dashboard.day }
                        Card {
                            Layout.fillWidth: true
                            Text {
                                text: dashboard.bridge.texts.expectedSun
                                color: Theme.muted
                                font.pointSize: Theme.labelSize
                                font.weight: Font.DemiBold
                                font.capitalization: Font.AllUppercase
                            }
                            SolarCurve { Layout.fillWidth: true; day: dashboard.day; nowText: dashboard.bridge.texts.now }
                        }
                        Card {
                            Layout.fillWidth: true
                            BatteryBar { Layout.fillWidth: true; day: dashboard.day; heading: dashboard.bridge.texts.battery }
                        }
                    }
                }
                Item { implicitHeight: Theme.gapXs }
            }
        }

        Rectangle { Layout.fillWidth: true; implicitHeight: 1; color: Theme.line }
        RowLayout {
            Layout.fillWidth: true
            Layout.margins: Theme.gapM
            Layout.leftMargin: Theme.gapL
            spacing: Theme.gapS
            Text {
                id: footer
                Layout.fillWidth: true
                text: dashboard.bridge.footer
                color: Theme.muted
                font.pointSize: Theme.labelSize
                wrapMode: Text.Wrap
                Accessible.role: Accessible.StaticText
                Accessible.name: text
                Accessible.description: dashboard.bridge.footerTooltip
                HoverHandler { id: footerHover }
                ToolTip.visible: footerHover.hovered && dashboard.bridge.footerTooltip !== ""
                ToolTip.text: dashboard.bridge.footerTooltip
            }
            ActionButton { text: dashboard.bridge.texts.refresh; hint: dashboard.bridge.texts.refreshHint; onClicked: dashboard.bridge.refresh() }
            ActionButton { text: dashboard.bridge.texts.away; onClicked: dashboard.bridge.requestAway() }
            ActionButton { text: dashboard.bridge.texts.settings; onClicked: dashboard.bridge.requestSettings() }
        }
    }
}
