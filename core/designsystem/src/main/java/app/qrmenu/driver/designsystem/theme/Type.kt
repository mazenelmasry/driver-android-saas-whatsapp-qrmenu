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
 * 🔴 Reduced a step at the body/title/label end (2026-09-21, project owner):
 * the earlier scale was set larger than POS's on the reasoning that a driver
 * reads one-handed in sunlight — true, but carried too far it produced screens
 * where three facts filled a phone and everything looked enlarged rather than
 * designed. The legibility argument is now served by the per-driver scale
 * control (`UiScaleStore`) instead of by a default nobody can turn down.
 *
 * The one exception is the money line: a card's amount still steps UP, because
 * it is the single value a driver reads from a car seat at arm's length. That
 * is done at the call site (`headlineSmall`), not by inflating the whole ramp.
 */
val DriverTypography = Typography(
    displayLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = DriverFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)
