/*
 * Six of Material's Filled icons, which material-icons-core lacks, copied as they are from
 * androidx.compose.material:material-icons-extended-android 1.7.8 (the Compose BOM's), whose path data comes from
 * google/material-design-icons. By The Android Open Source Project, under the Apache License 2.0; its text is in
 * android/legal/apache-2.0.txt. None of them is mirrored right to left in Material.
 */
package nl.enadeag.carchargeplanner.android.ui.settings

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Material's Filled icons that Settings' list shows and `material-icons-core` lacks, each built when first drawn. */
internal object SettingsIcons {

    /** Material's `Filled.SolarPower`: a panel under the sun, for the panel arrays. */
    val SolarPower: ImageVector by lazy {
        materialIcon(name = "Filled.SolarPower") {
            materialPath {
                moveTo(3.33f, 16.0f)
                lineToRelative(7.67f, 0.0f)
                lineToRelative(0.0f, -3.0f)
                lineToRelative(-7.0f, 0.0f)
                close()
            }
            materialPath {
                moveTo(13.0f, 16.0f)
                lineToRelative(7.67f, 0.0f)
                lineToRelative(-0.67f, -3.0f)
                lineToRelative(-7.0f, 0.0f)
                close()
            }
            materialPath {
                moveTo(21.11f, 18.0f)
                lineToRelative(-8.11f, 0.0f)
                lineToRelative(0.0f, 4.0f)
                lineToRelative(9.0f, 0.0f)
                close()
            }
            materialPath {
                moveTo(2.0f, 22.0f)
                lineToRelative(9.0f, 0.0f)
                lineToRelative(0.0f, -4.0f)
                lineToRelative(-8.11f, 0.0f)
                close()
            }
            materialPath {
                moveTo(11.0f, 8.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
            materialPath {
                moveTo(15.764f, 7.205f)
                lineToRelative(1.414f, -1.414f)
                lineToRelative(2.121f, 2.121f)
                lineToRelative(-1.414f, 1.414f)
                close()
            }
            materialPath {
                moveTo(4.705f, 7.913f)
                lineToRelative(2.121f, -2.121f)
                lineToRelative(1.414f, 1.414f)
                lineToRelative(-2.121f, 2.121f)
                close()
            }
            materialPath {
                moveTo(3.0f, 2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
            materialPath {
                moveTo(18.0f, 2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-3.0f)
                close()
            }
            materialPath {
                moveTo(12.0f, 7.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                horizontalLineTo(7.0f)
                curveTo(7.0f, 4.76f, 9.24f, 7.0f, 12.0f, 7.0f)
                close()
            }
        }
    }

    /** Material's `Filled.ElectricCar`: a car with a lightning bolt, for the car and the house. */
    val ElectricCar: ImageVector by lazy {
        materialIcon(name = "Filled.ElectricCar") {
            materialPath {
                moveTo(18.92f, 2.01f)
                curveTo(18.72f, 1.42f, 18.16f, 1.0f, 17.5f, 1.0f)
                horizontalLineToRelative(-11.0f)
                curveTo(5.84f, 1.0f, 5.29f, 1.42f, 5.08f, 2.01f)
                lineTo(3.0f, 8.0f)
                verticalLineToRelative(8.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(1.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(8.0f)
                lineTo(18.92f, 2.01f)
                close()
                moveTo(6.5f, 12.0f)
                curveTo(5.67f, 12.0f, 5.0f, 11.33f, 5.0f, 10.5f)
                reflectiveCurveTo(5.67f, 9.0f, 6.5f, 9.0f)
                reflectiveCurveTo(8.0f, 9.67f, 8.0f, 10.5f)
                reflectiveCurveTo(7.33f, 12.0f, 6.5f, 12.0f)
                close()
                moveTo(17.5f, 12.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(16.67f, 9.0f, 17.5f, 9.0f)
                reflectiveCurveTo(19.0f, 9.67f, 19.0f, 10.5f)
                reflectiveCurveTo(18.33f, 12.0f, 17.5f, 12.0f)
                close()
                moveTo(5.0f, 7.0f)
                lineToRelative(1.5f, -4.5f)
                horizontalLineToRelative(11.0f)
                lineTo(19.0f, 7.0f)
                horizontalLineTo(5.0f)
                close()
            }
            materialPath {
                moveTo(7.0f, 20.0f)
                lineToRelative(4.0f, 0.0f)
                lineToRelative(0.0f, -2.0f)
                lineToRelative(6.0f, 3.0f)
                lineToRelative(-4.0f, 0.0f)
                lineToRelative(0.0f, 2.0f)
                close()
            }
        }
    }

    /** Material's `Filled.EvStation`: a charging station, for the wall charger. */
    val EvStation: ImageVector by lazy {
        materialIcon(name = "Filled.EvStation") {
            materialPath {
                moveTo(19.77f, 7.23f)
                lineToRelative(0.01f, -0.01f)
                lineToRelative(-3.72f, -3.72f)
                lineTo(15.0f, 4.56f)
                lineToRelative(2.11f, 2.11f)
                curveToRelative(-0.94f, 0.36f, -1.61f, 1.26f, -1.61f, 2.33f)
                curveToRelative(0.0f, 1.38f, 1.12f, 2.5f, 2.5f, 2.5f)
                curveToRelative(0.36f, 0.0f, 0.69f, -0.08f, 1.0f, -0.21f)
                verticalLineToRelative(7.21f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineTo(14.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(-7.5f)
                horizontalLineToRelative(1.5f)
                verticalLineToRelative(5.0f)
                curveToRelative(0.0f, 1.38f, 1.12f, 2.5f, 2.5f, 2.5f)
                reflectiveCurveToRelative(2.5f, -1.12f, 2.5f, -2.5f)
                verticalLineTo(9.0f)
                curveToRelative(0.0f, -0.69f, -0.28f, -1.32f, -0.73f, -1.77f)
                close()
                moveTo(18.0f, 10.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                close()
                moveTo(8.0f, 18.0f)
                verticalLineToRelative(-4.5f)
                horizontalLineTo(6.0f)
                lineTo(10.0f, 6.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(2.0f)
                lineToRelative(-4.0f, 7.0f)
                close()
            }
        }
    }

    /** Material's `Filled.WbCloudy`: a cloud, for the forecast. */
    val WbCloudy: ImageVector by lazy {
        materialIcon(name = "Filled.WbCloudy") {
            materialPath {
                moveTo(19.36f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                horizontalLineToRelative(13.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.64f, -4.96f)
                close()
            }
        }
    }

    /** Material's `Filled.EventBusy`: a calendar with a cross, for the weekly away periods. */
    val EventBusy: ImageVector by lazy {
        materialIcon(name = "Filled.EventBusy") {
            materialPath {
                moveTo(9.31f, 17.0f)
                lineToRelative(2.44f, -2.44f)
                lineTo(14.19f, 17.0f)
                lineToRelative(1.06f, -1.06f)
                lineToRelative(-2.44f, -2.44f)
                lineToRelative(2.44f, -2.44f)
                lineTo(14.19f, 10.0f)
                lineToRelative(-2.44f, 2.44f)
                lineTo(9.31f, 10.0f)
                lineToRelative(-1.06f, 1.06f)
                lineToRelative(2.44f, 2.44f)
                lineToRelative(-2.44f, 2.44f)
                lineTo(9.31f, 17.0f)
                close()
                moveTo(19.0f, 3.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(18.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                lineTo(8.0f, 3.0f)
                lineTo(8.0f, 1.0f)
                lineTo(6.0f, 1.0f)
                verticalLineToRelative(2.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(3.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(19.0f, 19.0f)
                lineTo(5.0f, 19.0f)
                lineTo(5.0f, 8.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(11.0f)
                close()
            }
        }
    }

    /** Material's `Filled.Schedule`: a clock, for the planning preferences. */
    val Schedule: ImageVector by lazy {
        materialIcon(name = "Filled.Schedule") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
            materialPath {
                moveTo(12.5f, 7.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
    }
}
