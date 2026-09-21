package app.qrmenu.driver.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.qrmenu.driver.designsystem.R

/**
 * Brand typography — IBM Plex Sans Arabic (OFL, shipped as TTFs under res/font).
 *
 * **Script coverage, measured with fontTools on the shipped TTF (2026-09-21):**
 *  - Arabic + Latin + digits: covered.
 *  - Urdu: all twelve Urdu-specific letters covered (ٹ ڈ ڑ ں ھ ہ ی ے ک گ چ ژ).
 *  - Bengali and Devanagari: NOT covered — and no Arabic-designed face covers
 *    them. Android substitutes per missing glyph from the system (Noto), so bn
 *    and hi render correctly with nothing bundled. Do not bundle extra faces:
 *    it is APK weight for no gain.
 *
 * Chosen over Cairo (which also covers Urdu) because it carries 902 glyphs to
 * Cairo's 701 — fewer tofu boxes in odd names and addresses — it is the same
 * face the POS app uses, so a restaurant owner's two apps read as one family,
 * and its Latin digits are razor-sharp. This app is mostly numbers: amounts,
 * distances, the offer countdown, phone numbers.
 *
 * Numerals are always Latin (0-9) even on Arabic, pinned by LocaleManager's
 * `-u-nu-latn` extension plus the central money formatter.
 */
val DriverFontFamily: FontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/**
 * Type scale.
 *
 * Deliberately a step larger than the POS scale at the body/title end: this is
 * read one-handed, sometimes gloved, often in sunlight, and the amount to
 * collect has to be legible at a glance from a car seat.
 */
val DriverTypography = Typography(
    displayLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)
