import QtQuick
import QtQuick.Shapes

// The day's expected solar power as an area, with its charge windows, the grid charge, the time away and "now".
// A first sketch drawn with Qt Quick Shapes (in PySide6-Essentials); the finished chart comes later.
Item {
    id: chart
    required property var day
    property string nowText

    readonly property real plotTop: Theme.gapS
    readonly property real plotBottom: height - axis.implicitHeight - Theme.gapXs
    readonly property real plotLeft: kwLabel.implicitWidth + Theme.gapXs
    readonly property real maxKw: day.scaleKw

    function xAt(hour) { return plotLeft + (width - plotLeft) * hour / Math.max(1, day.hours) }
    function yAt(kw) { return plotBottom - (plotBottom - plotTop) * kw / maxKw }
    function area() {
        const points = day.solar
        if (points.length === 0)
            return []
        const out = [Qt.point(xAt(points[0].x), yAt(0))]
        for (let i = 0; i < points.length; i++)
            out.push(Qt.point(xAt(points[i].x), yAt(points[i].y)))
        out.push(Qt.point(xAt(points[points.length - 1].x), yAt(0)))
        return out
    }

    implicitHeight: Math.round(Theme.fontSize * 12)
    implicitWidth: Theme.popupWidth - 2 * Theme.gapL
    Accessible.role: Accessible.Chart
    Accessible.name: day.summary

    Repeater {
        model: chart.day.charges
        Rectangle {
            required property var modelData
            x: chart.xAt(modelData.start); width: chart.xAt(modelData.end) - x
            y: chart.plotTop; height: chart.plotBottom - chart.plotTop
            color: Theme.sun; opacity: 0.16; radius: Theme.gapXs
        }
    }
    Rectangle {
        visible: !!chart.day.grid
        x: visible ? chart.xAt(chart.day.grid.start) : 0; width: visible ? chart.xAt(chart.day.grid.end) - x : 0
        y: chart.plotTop; height: chart.plotBottom - chart.plotTop
        color: Theme.grid; opacity: 0.16; radius: Theme.gapXs
    }
    Repeater {
        model: chart.day.away
        Rectangle {
            required property var modelData
            x: chart.xAt(modelData.start); width: chart.xAt(modelData.end) - x
            y: chart.plotTop; height: chart.plotBottom - chart.plotTop
            color: Theme.faint; opacity: 0.14; radius: Theme.gapXs
        }
    }
    Rectangle { x: chart.plotLeft; y: chart.plotBottom; width: chart.width - chart.plotLeft; height: 1; color: Theme.line }

    Shape {
        anchors.fill: parent
        preferredRendererType: Shape.CurveRenderer
        ShapePath {
            fillColor: Theme.sunArea
            strokeColor: Theme.sun
            strokeWidth: 1.6
            joinStyle: ShapePath.RoundJoin
            PathPolyline { path: chart.area() }
        }
    }
    Shape {
        visible: chart.day.nowHour >= 0
        anchors.fill: parent
        ShapePath {
            strokeColor: Theme.fg
            strokeWidth: 1.2
            strokeStyle: ShapePath.DashLine
            dashPattern: [3, 3]
            fillColor: "transparent"
            startX: chart.xAt(Math.max(0, chart.day.nowHour)); startY: chart.plotTop
            PathLine { x: chart.xAt(Math.max(0, chart.day.nowHour)); y: chart.plotBottom }
        }
    }
    Text {
        visible: chart.day.nowHour >= 0
        x: chart.xAt(Math.max(0, chart.day.nowHour)) + Theme.gapXs; y: chart.plotTop
        text: chart.nowText
        color: Theme.fg
        font.pointSize: Theme.labelSize
    }

    Text { id: kwLabel; x: 0; y: chart.plotTop; text: chart.day.scaleText; color: Theme.muted; font.pointSize: Theme.labelSize }
    Item {
        id: axis
        y: chart.plotBottom + Theme.gapXs
        width: chart.width
        implicitHeight: kwLabel.implicitHeight
        Repeater {
            model: [0, 6, 12, 18]
            Text {
                required property int modelData
                x: chart.xAt(modelData) - (modelData === 0 ? 0 : width / 2)
                text: (modelData < 10 ? "0" : "") + modelData
                color: Theme.muted
                font.pointSize: Theme.labelSize
                font.features: { "tnum": 1 }
            }
        }
    }
}
