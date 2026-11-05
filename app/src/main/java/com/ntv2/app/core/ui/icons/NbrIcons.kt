package com.ntv2.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ícones próprios do NBR Play (os mesmos do protótipo).
 *
 * Arquivo GERADO por tools/icones/vd2kt.py a partir de res/drawable/ic_nbr_*.xml. Não edite à mão:
 * acrescente o ícone em tools/icones/nomes.txt, gere os drawables e rode o script de novo (docs/ICONES.md).
 */
object NbrIcons {
    val Activity: ImageVector by lazy { icon("ic_nbr_activity", false, "M 22 12 h -2.48 a 2 2 0 0 0 -1.93 1.46 l -2.35 8.36 a 0.25 0.25 0 0 1 -0.48 0 L 9.24 2.18 a 0.25 0.25 0 0 0 -0.48 0 l -2.35 8.36 A 2 2 0 0 1 4.49 12 H 2") }
    val ArrowLeft: ImageVector by lazy { icon("ic_nbr_arrow_left", false, "m 12 19 l -7 -7 l 7 -7", "M 19 12 H 5") }
    val ArrowLeftRight: ImageVector by lazy { icon("ic_nbr_arrow_left_right", false, "M 8 3 L 4 7 l 4 4", "M 4 7 h 16", "m 16 21 l 4 -4 l -4 -4", "M 20 17 H 4") }
    val ArrowRight: ImageVector by lazy { icon("ic_nbr_arrow_right", false, "M 5 12 h 14", "m 12 5 l 7 7 l -7 7") }
    val AudioLines: ImageVector by lazy { icon("ic_nbr_audio_lines", false, "M 2 10 v 3", "M 6 6 v 11", "M 10 3 v 18", "M 14 8 v 7", "M 18 5 v 13", "M 22 10 v 3") }
    val Bell: ImageVector by lazy { icon("ic_nbr_bell", false, "M 10.268 21 a 2 2 0 0 0 3.464 0", "M 3.262 15.326 A 1 1 0 0 0 4 17 h 16 a 1 1 0 0 0 0.74 -1.673 C 19.41 13.956 18 12.499 18 8 A 6 6 0 0 0 6 8 c 0 4.499 -1.411 5.956 -2.738 7.326") }
    val BellRing: ImageVector by lazy { icon("ic_nbr_bell_ring", false, "M 10.268 21 a 2 2 0 0 0 3.464 0", "M 22 8 c 0 -2.3 -0.8 -4.3 -2 -6", "M 3.262 15.326 A 1 1 0 0 0 4 17 h 16 a 1 1 0 0 0 0.74 -1.673 C 19.41 13.956 18 12.499 18 8 A 6 6 0 0 0 6 8 c 0 4.499 -1.411 5.956 -2.738 7.326", "M 4 2 C 2.8 3.7 2 5.7 2 8") }
    val Calendar: ImageVector by lazy { icon("ic_nbr_calendar", false, "M 8 2 v 3", "M 16 2 v 3", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z", "M 3 9 h 18") }
    val Captions: ImageVector by lazy { icon("ic_nbr_captions", false, "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M 7 15 h 4 M 15 15 h 2 M 7 11 h 2 M 13 11 h 4") }
    val Cast: ImageVector by lazy { icon("ic_nbr_cast", false, "M 2 8 V 6 a 2 2 0 0 1 2 -2 h 16 a 2 2 0 0 1 2 2 v 12 a 2 2 0 0 1 -2 2 h -6", "M 2 12 a 9 9 0 0 1 8 8", "M 2 16 a 5 5 0 0 1 4 4", "M2 20L2.01 20") }
    val Check: ImageVector by lazy { icon("ic_nbr_check", false, "M 20 6 L 9 17 l -5 -5") }
    val CheckCheck: ImageVector by lazy { icon("ic_nbr_check_check", false, "M 18 6 L 7 17 l -5 -5", "m 22 10 l -7.5 7.5 L 13 16") }
    val ChevronDown: ImageVector by lazy { icon("ic_nbr_chevron_down", false, "m 6 9 l 6 6 l 6 -6") }
    val ChevronLeft: ImageVector by lazy { icon("ic_nbr_chevron_left", false, "m 15 18 l -6 -6 l 6 -6") }
    val ChevronRight: ImageVector by lazy { icon("ic_nbr_chevron_right", false, "m 9 18 l 6 -6 l -6 -6") }
    val Circle: ImageVector by lazy { icon("ic_nbr_circle", false, "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z") }
    val CircleAlert: ImageVector by lazy { icon("ic_nbr_circle_alert", false, "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z", "M12 8L12 12", "M12 16L12.01 16") }
    val CircleCheck: ImageVector by lazy { icon("ic_nbr_circle_check", false, "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z", "m 16 9 l -5.5 5.5 L 8 12") }
    val CircleDot: ImageVector by lazy { icon("ic_nbr_circle_dot", false, "M11 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0z", "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z") }
    val CircleX: ImageVector by lazy { icon("ic_nbr_circle_x", false, "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z", "m 15 9 l -6 6", "m 9 9 l 6 6") }
    val Clapperboard: ImageVector by lazy { icon("ic_nbr_clapperboard", false, "m 12.296 3.464 l 3.02 3.956", "M 20.2 6 L 3 11 l -0.9 -2.4 c -0.3 -1.1 0.3 -2.2 1.3 -2.5 l 13.5 -4 c 1.1 -0.3 2.2 0.3 2.5 1.3z", "M 3 11 h 18 v 8 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2z", "m 6.18 5.276 l 3.1 3.899") }
    val Cloud: ImageVector by lazy { icon("ic_nbr_cloud", false, "M 17.5 19 H 9 a 7 7 0 1 1 6.71 -9 h 1.79 a 4.5 4.5 0 1 1 0 9Z") }
    val Copy: ImageVector by lazy { icon("ic_nbr_copy", false, "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M 4 16 c -1.1 0 -2 -0.9 -2 -2 V 4 c 0 -1.1 0.9 -2 2 -2 h 10 c 1.1 0 2 0.9 2 2") }
    val CreditCard: ImageVector by lazy { icon("ic_nbr_credit_card", false, "M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M2 10L22 10", "M 6 14 h 2") }
    val Crown: ImageVector by lazy { icon("ic_nbr_crown", false, "M 11.562 3.266 a 0.5 0.5 0 0 1 0.876 0 L 15.39 8.87 a 1 1 0 0 0 1.516 0.294 L 21.183 5.5 a 0.5 0.5 0 0 1 0.798 0.519 l -2.834 10.246 a 1 1 0 0 1 -0.956 0.734 H 5.81 a 1 1 0 0 1 -0.957 -0.734 L 2.02 6.02 a 0.5 0.5 0 0 1 0.798 -0.519 l 4.276 3.664 a 1 1 0 0 0 1.516 -0.294z", "M 5 21 h 14") }
    val Database: ImageVector by lazy { icon("ic_nbr_database", false, "M3 5 a9 3 0 1 0 18 0 a9 3 0 1 0 -18 0z", "M 3 5 V 19 A 9 3 0 0 0 21 19 V 5", "M 3 12 A 9 3 0 0 0 21 12") }
    val Download: ImageVector by lazy { icon("ic_nbr_download", false, "M 12 15 V 3", "M 21 15 v 4 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 v -4", "m 7 10 l 5 5 l 5 -5") }
    val Ellipsis: ImageVector by lazy { icon("ic_nbr_ellipsis", false, "M11 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0z", "M18 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0z", "M4 12 a1 1 0 1 0 2 0 a1 1 0 1 0 -2 0z") }
    val Eye: ImageVector by lazy { icon("ic_nbr_eye", false, "M 2.062 12.348 a 1 1 0 0 1 0 -0.696 a 10.75 10.75 0 0 1 19.876 0 a 1 1 0 0 1 0 0.696 a 10.75 10.75 0 0 1 -19.876 0", "M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0z") }
    val FastForward: ImageVector by lazy { icon("ic_nbr_fast_forward", false, "M 12 6 a 2 2 0 0 1 3.414 -1.414 l 6 6 a 2 2 0 0 1 0 2.828 l -6 6 A 2 2 0 0 1 12 18z", "M 2 6 a 2 2 0 0 1 3.414 -1.414 l 6 6 a 2 2 0 0 1 0 2.828 l -6 6 A 2 2 0 0 1 2 18z") }
    val FastForwardFilled: ImageVector by lazy { icon("ic_nbr_fast_forward_filled", true, "M 12 6 a 2 2 0 0 1 3.414 -1.414 l 6 6 a 2 2 0 0 1 0 2.828 l -6 6 A 2 2 0 0 1 12 18z", "M 2 6 a 2 2 0 0 1 3.414 -1.414 l 6 6 a 2 2 0 0 1 0 2.828 l -6 6 A 2 2 0 0 1 2 18z") }
    val Flag: ImageVector by lazy { icon("ic_nbr_flag", false, "M 4 22 V 4 a 1 1 0 0 1 0.4 -0.8 A 6 6 0 0 1 8 2 c 3 0 5 2 7.333 2 q 2 0 3.067 -0.8 A 1 1 0 0 1 20 4 v 10 a 1 1 0 0 1 -0.4 0.8 A 6 6 0 0 1 16 16 c -3 0 -5 -2 -8 -2 a 6 6 0 0 0 -4 1.528") }
    val FunnelX: ImageVector by lazy { icon("ic_nbr_funnel_x", false, "M 12.531 3 H 3 a 1 1 0 0 0 -0.742 1.67 l 7.225 7.989 A 2 2 0 0 1 10 14 v 6 a 1 1 0 0 0 0.553 0.895 l 2 1 A 1 1 0 0 0 14 21 v -7 a 2 2 0 0 1 0.517 -1.341 l 0.427 -0.473", "m 16.5 3.5 l 5 5", "m 21.5 3.5 l -5 5") }
    val Gauge: ImageVector by lazy { icon("ic_nbr_gauge", false, "m 12 14 l 4 -4", "M 3.34 19 a 10 10 0 1 1 17.32 0") }
    val Globe: ImageVector by lazy { icon("ic_nbr_globe", false, "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z", "M 12 2 a 14.5 14.5 0 0 0 0 20 a 14.5 14.5 0 0 0 0 -20", "M 2 12 h 20") }
    val Heart: ImageVector by lazy { icon("ic_nbr_heart", false, "M 2 9.5 a 5.5 5.5 0 0 1 9.591 -3.676 a 0.56 0.56 0 0 0 0.818 0 A 5.49 5.49 0 0 1 22 9.5 c 0 2.29 -1.5 4 -3 5.5 l -5.492 5.313 a 2 2 0 0 1 -3 0.019 L 5 15 c -1.5 -1.5 -3 -3.2 -3 -5.5") }
    val HeartFilled: ImageVector by lazy { icon("ic_nbr_heart_filled", true, "M 2 9.5 a 5.5 5.5 0 0 1 9.591 -3.676 a 0.56 0.56 0 0 0 0.818 0 A 5.49 5.49 0 0 1 22 9.5 c 0 2.29 -1.5 4 -3 5.5 l -5.492 5.313 a 2 2 0 0 1 -3 0.019 L 5 15 c -1.5 -1.5 -3 -3.2 -3 -5.5") }
    val History: ImageVector by lazy { icon("ic_nbr_history", false, "M 3 12 a 9 9 0 1 0 9 -9 a 9.75 9.75 0 0 0 -6.74 2.74 L 3 8", "M 3 3 v 5 h 5", "M 12 7 v 5 l 4 2") }
    val Hourglass: ImageVector by lazy { icon("ic_nbr_hourglass", false, "M 5 22 h 14", "M 5 2 h 14", "M 17 22 v -4.172 a 2 2 0 0 0 -0.586 -1.414 L 12 12 l -4.414 4.414 A 2 2 0 0 0 7 17.828 V 22", "M 7 2 v 4.172 a 2 2 0 0 0 0.586 1.414 L 12 12 l 4.414 -4.414 A 2 2 0 0 0 17 6.172 V 2") }
    val House: ImageVector by lazy { icon("ic_nbr_house", false, "M 15 21 v -8 a 1 1 0 0 0 -1 -1 h -4 a 1 1 0 0 0 -1 1 v 8", "M 3 10 a 2 2 0 0 1 0.709 -1.528 l 7 -6 a 2 2 0 0 1 2.582 0 l 7 6 A 2 2 0 0 1 21 10 v 9 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2z") }
    val Image: ImageVector by lazy { icon("ic_nbr_image", false, "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z", "M7 9 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0z", "m 21 15 l -3.086 -3.086 a 2 2 0 0 0 -2.828 0 L 6 21") }
    val ImageOff: ImageVector by lazy { icon("ic_nbr_image_off", false, "M2 2L22 22", "M 10.41 10.41 a 2 2 0 1 1 -2.83 -2.83", "M13.5 13.5L6 21", "M18 12L21 15", "M 3.59 3.59 A 1.99 1.99 0 0 0 3 5 v 14 a 2 2 0 0 0 2 2 h 14 c 0.55 0 1.052 -0.22 1.41 -0.59", "M 21 15 V 5 a 2 2 0 0 0 -2 -2 H 9") }
    val Info: ImageVector by lazy { icon("ic_nbr_info", false, "M2 12 a10 10 0 1 0 20 0 a10 10 0 1 0 -20 0z", "M 12 16 v -4", "M 12 8 h 0.01") }
    val Keyboard: ImageVector by lazy { icon("ic_nbr_keyboard", false, "M 10 8 h 0.01", "M 12 12 h 0.01", "M 14 8 h 0.01", "M 16 12 h 0.01", "M 18 8 h 0.01", "M 6 8 h 0.01", "M 7 16 h 10", "M 8 12 h 0.01", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z") }
    val Layers: ImageVector by lazy { icon("ic_nbr_layers", false, "M 12.83 2.18 a 2 2 0 0 0 -1.66 0 L 2.6 6.08 a 1 1 0 0 0 0 1.83 l 8.58 3.91 a 2 2 0 0 0 1.66 0 l 8.58 -3.9 a 1 1 0 0 0 0 -1.83z", "M 2 12 a 1 1 0 0 0 0.58 0.91 l 8.6 3.91 a 2 2 0 0 0 1.65 0 l 8.58 -3.9 A 1 1 0 0 0 22 12", "M 2 17 a 1 1 0 0 0 0.58 0.91 l 8.6 3.91 a 2 2 0 0 0 1.65 0 l 8.58 -3.9 A 1 1 0 0 0 22 17") }
    val Library: ImageVector by lazy { icon("ic_nbr_library", false, "m 16 6 l 4 14", "M 12 6 v 14", "M 8 8 v 12", "M 4 4 v 16") }
    val Link: ImageVector by lazy { icon("ic_nbr_link", false, "M 10 13 a 5 5 0 0 0 7.54 0.54 l 3 -3 a 5 5 0 0 0 -7.07 -7.07 l -1.72 1.71", "M 14 11 a 5 5 0 0 0 -7.54 -0.54 l -3 3 a 5 5 0 0 0 7.07 7.07 l 1.71 -1.71") }
    val Loader: ImageVector by lazy { icon("ic_nbr_loader", false, "M 12 2 v 4", "m 16.2 7.8 l 2.9 -2.9", "M 18 12 h 4", "m 16.2 16.2 l 2.9 2.9", "M 12 18 v 4", "m 4.9 19.1 l 2.9 -2.9", "M 2 12 h 4", "m 4.9 4.9 l 2.9 2.9") }
    val Lock: ImageVector by lazy { icon("ic_nbr_lock", false, "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z", "M 7 11 V 7 a 5 5 0 0 1 10 0 v 4") }
    val LogOut: ImageVector by lazy { icon("ic_nbr_log_out", false, "m 16 17 l 5 -5 l -5 -5", "M 21 12 H 9", "M 9 21 H 5 a 2 2 0 0 1 -2 -2 V 5 a 2 2 0 0 1 2 -2 h 4") }
    val Maximize: ImageVector by lazy { icon("ic_nbr_maximize", false, "M 8 3 H 5 a 2 2 0 0 0 -2 2 v 3", "M 21 8 V 5 a 2 2 0 0 0 -2 -2 h -3", "M 3 16 v 3 a 2 2 0 0 0 2 2 h 3", "M 16 21 h 3 a 2 2 0 0 0 2 -2 v -3") }
    val MessageSquarePlus: ImageVector by lazy { icon("ic_nbr_message_square_plus", false, "M 22 17 a 2 2 0 0 1 -2 2 H 6.828 a 2 2 0 0 0 -1.414 0.586 l -2.202 2.202 A 0.71 0.71 0 0 1 2 21.286 V 5 a 2 2 0 0 1 2 -2 h 16 a 2 2 0 0 1 2 2z", "M 12 8 v 6", "M 9 11 h 6") }
    val MessageSquareWarning: ImageVector by lazy { icon("ic_nbr_message_square_warning", false, "M 22 17 a 2 2 0 0 1 -2 2 H 6.828 a 2 2 0 0 0 -1.414 0.586 l -2.202 2.202 A 0.71 0.71 0 0 1 2 21.286 V 5 a 2 2 0 0 1 2 -2 h 16 a 2 2 0 0 1 2 2z", "M 12 15 h 0.01", "M 12 7 v 4") }
    val Mic: ImageVector by lazy { icon("ic_nbr_mic", false, "M 12 19 v 3", "M 19 10 v 2 a 7 7 0 0 1 -14 0 v -2", "M12 2h0a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3z") }
    val Minus: ImageVector by lazy { icon("ic_nbr_minus", false, "M 5 12 h 14") }
    val Monitor: ImageVector by lazy { icon("ic_nbr_monitor", false, "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M8 21L16 21", "M12 17L12 21") }
    val MonitorX: ImageVector by lazy { icon("ic_nbr_monitor_x", false, "m 14.5 12.5 l -5 -5", "m 9.5 12.5 l 5 -5", "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2z", "M 12 17 v 4", "M 8 21 h 8") }
    val Pause: ImageVector by lazy { icon("ic_nbr_pause", false, "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z", "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z") }
    val PauseFilled: ImageVector by lazy { icon("ic_nbr_pause_filled", true, "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z", "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z") }
    val Pencil: ImageVector by lazy { icon("ic_nbr_pencil", false, "M 21.174 6.812 a 1 1 0 0 0 -3.986 -3.987 L 3.842 16.174 a 2 2 0 0 0 -0.5 0.83 l -1.321 4.352 a 0.5 0.5 0 0 0 0.623 0.622 l 4.353 -1.32 a 2 2 0 0 0 0.83 -0.497z", "m 15 5 l 4 4") }
    val PictureInPicture2: ImageVector by lazy { icon("ic_nbr_picture_in_picture_2", false, "M 21 9 V 6 a 2 2 0 0 0 -2 -2 H 4 a 2 2 0 0 0 -2 2 v 10 c 0 1.1 0.9 2 2 2 h 4", "M14 13h6a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-6a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2z") }
    val Play: ImageVector by lazy { icon("ic_nbr_play", false, "M 5 5 a 2 2 0 0 1 3.008 -1.728 l 11.997 6.998 a 2 2 0 0 1 0.003 3.458 l -12 7 A 2 2 0 0 1 5 19z") }
    val PlayFilled: ImageVector by lazy { icon("ic_nbr_play_filled", true, "M 5 5 a 2 2 0 0 1 3.008 -1.728 l 11.997 6.998 a 2 2 0 0 1 0.003 3.458 l -12 7 A 2 2 0 0 1 5 19z") }
    val Plus: ImageVector by lazy { icon("ic_nbr_plus", false, "M 5 12 h 14", "M 12 5 v 14") }
    val QrCode: ImageVector by lazy { icon("ic_nbr_qr_code", false, "M4 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M17 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M4 16h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M 21 16 h -3 a 2 2 0 0 0 -2 2 v 3", "M 21 21 v 0.01", "M 12 7 v 3 a 2 2 0 0 1 -2 2 H 7", "M 3 12 h 0.01", "M 12 3 h 0.01", "M 12 16 v 0.01", "M 16 12 h 1", "M 21 12 v 0.01", "M 12 21 v -1") }
    val Receipt: ImageVector by lazy { icon("ic_nbr_receipt", false, "M 12 17 V 7", "M 16 8 h -6 a 2 2 0 0 0 0 4 h 4 a 2 2 0 0 1 0 4 H 8", "M 4 3 a 1 1 0 0 1 1 -1 a 1.3 1.3 0 0 1 0.7 0.2 l 0.933 0.6 a 1.3 1.3 0 0 0 1.4 0 l 0.934 -0.6 a 1.3 1.3 0 0 1 1.4 0 l 0.933 0.6 a 1.3 1.3 0 0 0 1.4 0 l 0.933 -0.6 a 1.3 1.3 0 0 1 1.4 0 l 0.934 0.6 a 1.3 1.3 0 0 0 1.4 0 l 0.933 -0.6 A 1.3 1.3 0 0 1 19 2 a 1 1 0 0 1 1 1 v 18 a 1 1 0 0 1 -1 1 a 1.3 1.3 0 0 1 -0.7 -0.2 l -0.933 -0.6 a 1.3 1.3 0 0 0 -1.4 0 l -0.934 0.6 a 1.3 1.3 0 0 1 -1.4 0 l -0.933 -0.6 a 1.3 1.3 0 0 0 -1.4 0 l -0.933 0.6 a 1.3 1.3 0 0 1 -1.4 0 l -0.934 -0.6 a 1.3 1.3 0 0 0 -1.4 0 l -0.933 0.6 a 1.3 1.3 0 0 1 -0.7 0.2 a 1 1 0 0 1 -1 -1z") }
    val RefreshCw: ImageVector by lazy { icon("ic_nbr_refresh_cw", false, "M 3 12 a 9 9 0 0 1 9 -9 a 9.75 9.75 0 0 1 6.74 2.74 L 21 8", "M 21 3 v 5 h -5", "M 21 12 a 9 9 0 0 1 -9 9 a 9.75 9.75 0 0 1 -6.74 -2.74 L 3 16", "M 8 16 H 3 v 5") }
    val Rewind: ImageVector by lazy { icon("ic_nbr_rewind", false, "M 12 6 a 2 2 0 0 0 -3.414 -1.414 l -6 6 a 2 2 0 0 0 0 2.828 l 6 6 A 2 2 0 0 0 12 18z", "M 22 6 a 2 2 0 0 0 -3.414 -1.414 l -6 6 a 2 2 0 0 0 0 2.828 l 6 6 A 2 2 0 0 0 22 18z") }
    val RewindFilled: ImageVector by lazy { icon("ic_nbr_rewind_filled", true, "M 12 6 a 2 2 0 0 0 -3.414 -1.414 l -6 6 a 2 2 0 0 0 0 2.828 l 6 6 A 2 2 0 0 0 12 18z", "M 22 6 a 2 2 0 0 0 -3.414 -1.414 l -6 6 a 2 2 0 0 0 0 2.828 l 6 6 A 2 2 0 0 0 22 18z") }
    val RotateCcw: ImageVector by lazy { icon("ic_nbr_rotate_ccw", false, "M 3 12 a 9 9 0 1 0 9 -9 a 9.75 9.75 0 0 0 -6.74 2.74 L 3 8", "M 3 3 v 5 h 5") }
    val RotateCw: ImageVector by lazy { icon("ic_nbr_rotate_cw", false, "M 21 12 a 9 9 0 1 1 -9 -9 c 2.52 0 4.93 1 6.74 2.74 L 21 8", "M 21 3 v 5 h -5") }
    val Search: ImageVector by lazy { icon("ic_nbr_search", false, "m 21 21 l -4.34 -4.34", "M3 11 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0z") }
    val SearchX: ImageVector by lazy { icon("ic_nbr_search_x", false, "m 13.5 8.5 l -5 5", "m 8.5 8.5 l 5 5", "M3 11 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0z", "m 21 21 l -4.3 -4.3") }
    val Send: ImageVector by lazy { icon("ic_nbr_send", false, "M 14.536 21.686 a 0.5 0.5 0 0 0 0.937 -0.024 l 6.5 -19 a 0.496 0.496 0 0 0 -0.635 -0.635 l -19 6.5 a 0.5 0.5 0 0 0 -0.024 0.937 l 7.93 3.18 a 2 2 0 0 1 1.112 1.11z", "m 21.854 2.147 l -10.94 10.939") }
    val Settings: ImageVector by lazy { icon("ic_nbr_settings", false, "M 9.671 4.136 a 2.34 2.34 0 0 1 4.659 0 a 2.34 2.34 0 0 0 3.319 1.915 a 2.34 2.34 0 0 1 2.33 4.033 a 2.34 2.34 0 0 0 0 3.831 a 2.34 2.34 0 0 1 -2.33 4.033 a 2.34 2.34 0 0 0 -3.319 1.915 a 2.34 2.34 0 0 1 -4.659 0 a 2.34 2.34 0 0 0 -3.32 -1.915 a 2.34 2.34 0 0 1 -2.33 -4.033 a 2.34 2.34 0 0 0 0 -3.831 A 2.34 2.34 0 0 1 6.35 6.051 a 2.34 2.34 0 0 0 3.319 -1.915", "M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0z") }
    val Share2: ImageVector by lazy { icon("ic_nbr_share_2", false, "M15 5 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0z", "M3 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0z", "M15 19 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0z", "M8.59 13.51L15.42 17.49", "M15.41 6.51L8.59 10.49") }
    val Shield: ImageVector by lazy { icon("ic_nbr_shield", false, "M 20 13 c 0 5 -3.5 7.5 -7.66 8.95 a 1 1 0 0 1 -0.67 -0.01 C 7.5 20.5 4 18 4 13 V 6 a 1 1 0 0 1 1 -1 c 2 0 4.5 -1.2 6.24 -2.72 a 1.17 1.17 0 0 1 1.52 0 C 14.51 3.81 17 5 19 5 a 1 1 0 0 1 1 1z") }
    val ShieldCheck: ImageVector by lazy { icon("ic_nbr_shield_check", false, "M 20 13 c 0 5 -3.5 7.5 -7.66 8.95 a 1 1 0 0 1 -0.67 -0.01 C 7.5 20.5 4 18 4 13 V 6 a 1 1 0 0 1 1 -1 c 2 0 4.5 -1.2 6.24 -2.72 a 1.17 1.17 0 0 1 1.52 0 C 14.51 3.81 17 5 19 5 a 1 1 0 0 1 1 1z", "m 9 12 l 2 2 l 4 -4") }
    val SkipForward: ImageVector by lazy { icon("ic_nbr_skip_forward", false, "M 21 4 v 16", "M 6.029 4.285 A 2 2 0 0 0 3 6 v 12 a 2 2 0 0 0 3.029 1.715 l 9.997 -5.998 a 2 2 0 0 0 0.003 -3.432z") }
    val SkipForwardFilled: ImageVector by lazy { icon("ic_nbr_skip_forward_filled", true, "M 21 4 v 16", "M 6.029 4.285 A 2 2 0 0 0 3 6 v 12 a 2 2 0 0 0 3.029 1.715 l 9.997 -5.998 a 2 2 0 0 0 0.003 -3.432z") }
    val SlidersHorizontal: ImageVector by lazy { icon("ic_nbr_sliders_horizontal", false, "M 10 5 H 3", "M 12 19 H 3", "M 14 3 v 4", "M 16 17 v 4", "M 21 12 h -9", "M 21 19 h -5", "M 21 5 h -7", "M 8 10 v 4", "M 8 12 H 3") }
    val Smartphone: ImageVector by lazy { icon("ic_nbr_smartphone", false, "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z", "M 12 18 h 0.01") }
    val Sparkles: ImageVector by lazy { icon("ic_nbr_sparkles", false, "M 11.017 2.814 a 1 1 0 0 1 1.966 0 l 1.051 5.558 a 2 2 0 0 0 1.594 1.594 l 5.558 1.051 a 1 1 0 0 1 0 1.966 l -5.558 1.051 a 2 2 0 0 0 -1.594 1.594 l -1.051 5.558 a 1 1 0 0 1 -1.966 0 l -1.051 -5.558 a 2 2 0 0 0 -1.594 -1.594 l -5.558 -1.051 a 1 1 0 0 1 0 -1.966 l 5.558 -1.051 a 2 2 0 0 0 1.594 -1.594z", "M 20 2 v 4", "M 22 4 h -4", "M2 20 a2 2 0 1 0 4 0 a2 2 0 1 0 -4 0z") }
    val SquareDashed: ImageVector by lazy { icon("ic_nbr_square_dashed", false, "M 5 3 a 2 2 0 0 0 -2 2", "M 19 3 a 2 2 0 0 1 2 2", "M 21 19 a 2 2 0 0 1 -2 2", "M 5 21 a 2 2 0 0 1 -2 -2", "M 9 3 h 1", "M 9 21 h 1", "M 14 3 h 1", "M 14 21 h 1", "M 3 9 v 1", "M 21 9 v 1", "M 3 14 v 1", "M 21 14 v 1") }
    val Star: ImageVector by lazy { icon("ic_nbr_star", false, "M 11.525 2.295 a 0.53 0.53 0 0 1 0.95 0 l 2.31 4.679 a 2.123 2.123 0 0 0 1.595 1.16 l 5.166 0.756 a 0.53 0.53 0 0 1 0.294 0.904 l -3.736 3.638 a 2.123 2.123 0 0 0 -0.611 1.878 l 0.882 5.14 a 0.53 0.53 0 0 1 -0.771 0.56 l -4.618 -2.428 a 2.122 2.122 0 0 0 -1.973 0 L 6.396 21.01 a 0.53 0.53 0 0 1 -0.77 -0.56 l 0.881 -5.139 a 2.122 2.122 0 0 0 -0.611 -1.879 L 2.16 9.795 a 0.53 0.53 0 0 1 0.294 -0.906 l 5.165 -0.755 a 2.122 2.122 0 0 0 1.597 -1.16z") }
    val StarFilled: ImageVector by lazy { icon("ic_nbr_star_filled", true, "M 11.525 2.295 a 0.53 0.53 0 0 1 0.95 0 l 2.31 4.679 a 2.123 2.123 0 0 0 1.595 1.16 l 5.166 0.756 a 0.53 0.53 0 0 1 0.294 0.904 l -3.736 3.638 a 2.123 2.123 0 0 0 -0.611 1.878 l 0.882 5.14 a 0.53 0.53 0 0 1 -0.771 0.56 l -4.618 -2.428 a 2.122 2.122 0 0 0 -1.973 0 L 6.396 21.01 a 0.53 0.53 0 0 1 -0.77 -0.56 l 0.881 -5.139 a 2.122 2.122 0 0 0 -0.611 -1.879 L 2.16 9.795 a 0.53 0.53 0 0 1 0.294 -0.906 l 5.165 -0.755 a 2.122 2.122 0 0 0 1.597 -1.16z") }
    val Sun: ImageVector by lazy { icon("ic_nbr_sun", false, "M8 12 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0z", "M 12 2 v 2", "M 12 20 v 2", "m 4.93 4.93 l 1.41 1.41", "m 17.66 17.66 l 1.41 1.41", "M 2 12 h 2", "M 20 12 h 2", "m 6.34 17.66 l -1.41 1.41", "m 19.07 4.93 l -1.41 1.41") }
    val Tablet: ImageVector by lazy { icon("ic_nbr_tablet", false, "M6 2h12a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2z", "M12 18L12.01 18") }
    val ThumbsUp: ImageVector by lazy { icon("ic_nbr_thumbs_up", false, "M 15 5.88 L 14 10 h 5.83 a 2 2 0 0 1 1.92 2.56 l -2.33 8 A 2 2 0 0 1 17.5 22 H 4 a 2 2 0 0 1 -2 -2 v -8 a 2 2 0 0 1 2 -2 h 2.76 a 2 2 0 0 0 1.79 -1.11 L 12 2 a 3.13 3.13 0 0 1 3 3.88Z", "M 7 10 v 12") }
    val Timer: ImageVector by lazy { icon("ic_nbr_timer", false, "M10 2L14 2", "M12 14L15 11", "M4 14 a8 8 0 1 0 16 0 a8 8 0 1 0 -16 0z") }
    val Trash2: ImageVector by lazy { icon("ic_nbr_trash_2", false, "M 10 11 v 6", "M 14 11 v 6", "M 19 6 v 14 a 2 2 0 0 1 -2 2 H 7 a 2 2 0 0 1 -2 -2 V 6", "M 3 6 h 18", "M 8 6 V 4 a 2 2 0 0 1 2 -2 h 4 a 2 2 0 0 1 2 2 v 2") }
    val TriangleAlert: ImageVector by lazy { icon("ic_nbr_triangle_alert", false, "m 21.73 18 l -8 -14 a 2 2 0 0 0 -3.48 0 l -8 14 A 2 2 0 0 0 4 21 h 16 a 2 2 0 0 0 1.73 -3", "M 12 9 v 4", "M 12 17 h 0.01") }
    val Tv: ImageVector by lazy { icon("ic_nbr_tv", false, "m 17 2 l -5 5 l -5 -5", "M4 7h16a2 2 0 0 1 2 2v11a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-11a2 2 0 0 1 2 -2z") }
    val Unlink: ImageVector by lazy { icon("ic_nbr_unlink", false, "m 18.84 12.25 l 1.72 -1.71 h -0.02 a 5.004 5.004 0 0 0 -0.12 -7.07 a 5.006 5.006 0 0 0 -6.95 0 l -1.72 1.71", "m 5.17 11.75 l -1.71 1.71 a 5.004 5.004 0 0 0 0.12 7.07 a 5.006 5.006 0 0 0 6.95 0 l 1.71 -1.71", "M8 2L8 5", "M2 8L5 8", "M16 19L16 22", "M19 16L22 16") }
    val Users: ImageVector by lazy { icon("ic_nbr_users", false, "M 16 21 v -2 a 4 4 0 0 0 -4 -4 H 6 a 4 4 0 0 0 -4 4 v 2", "M 16 3.128 a 4 4 0 0 1 0 7.744", "M 22 21 v -2 a 4 4 0 0 0 -3 -3.87", "M5 7 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0z") }
    val Volume2: ImageVector by lazy { icon("ic_nbr_volume_2", false, "M 11 4.702 a 0.705 0.705 0 0 0 -1.203 -0.498 L 6.413 7.587 A 1.4 1.4 0 0 1 5.416 8 H 3 a 1 1 0 0 0 -1 1 v 6 a 1 1 0 0 0 1 1 h 2.416 a 1.4 1.4 0 0 1 0.997 0.413 l 3.383 3.384 A 0.705 0.705 0 0 0 11 19.298z", "M 16 9 a 5 5 0 0 1 0 6", "M 19.364 18.364 a 9 9 0 0 0 0 -12.728") }
    val VolumeX: ImageVector by lazy { icon("ic_nbr_volume_x", false, "M 11 4.702 a 0.7 0.7 0 0 0 -1.203 -0.498 L 6.413 7.587 A 1.4 1.4 0 0 1 5.416 8 H 3 a 1 1 0 0 0 -1 1 v 6 a 1 1 0 0 0 1 1 h 2.416 a 1.4 1.4 0 0 1 0.997 0.413 l 3.383 3.384 A 0.7 0.7 0 0 0 11 19.298z", "m 16.5 14.5 l 5 -5", "m 16.5 9.5 l 5 5") }
    val Wifi: ImageVector by lazy { icon("ic_nbr_wifi", false, "M 12 20 h 0.01", "M 2 8.82 a 15 15 0 0 1 20 0", "M 5 12.859 a 10 10 0 0 1 14 0", "M 8.5 16.429 a 5 5 0 0 1 7 0") }
    val WifiOff: ImageVector by lazy { icon("ic_nbr_wifi_off", false, "M 12 20 h 0.01", "M 8.5 16.429 a 5 5 0 0 1 7 0", "M 5 12.859 a 10 10 0 0 1 5.17 -2.69", "M 19 12.859 a 10 10 0 0 0 -2.007 -1.523", "M 2 8.82 a 15 15 0 0 1 4.177 -2.643", "M 22 8.82 a 15 15 0 0 0 -11.288 -3.764", "m 2 2 l 20 20") }
    val X: ImageVector by lazy { icon("ic_nbr_x", false, "M 18 6 L 6 18", "m 6 6 l 12 12") }
}

private fun icon(name: String, filled: Boolean, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    for (d in paths) {
        b.addPath(
            pathData = addPathNodes(d),
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return b.build()
}
