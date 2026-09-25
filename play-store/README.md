# حزمة نشر تطبيق السائقين على Google Play — دليل التسليم

## ✅ ما جُهِّز فعلاً (ليلة 2026-09-25)

| البند | الحالة | أين |
|---|---|---|
| مفتاح الرفع (upload key) | ✅ مُنشأ، RSA 4096، صالح حتى 2054 | `~/driver-signing/driver-upload.jks` + `signing.env` (**خارج الريبو**) |
| بصمة مفتاح الرفع | SHA-1 `48:C7:36:A5:2D:14:CE:0B:31:5E:FE:CC:8E:24:CB:04:03:25:8F:5C` · SHA-256 `AC:79:54:55:6C:87:B4:7A:28:4E:3E:1C:F2:50:64:9F:49:D2:64:6C:D0:3B:66:18:11:54:61:4C:16:F7:23:13` | — |
| `targetSdk`/`compileSdk` | ✅ 36 (شرط Play منذ 31 أغسطس 2026) — مُجرَّب على S25 (Android 16) | `gradle/libs.versions.toml` |
| حزم الرفع AAB (موقّعة بمفتاح الرفع) | ✅ `app-meniura-release.aab` · `app-taaj-release.aab` (1.0.0 / 10000) | `app/build/outputs/bundle/*Release/` — أعِد بناءها بـ`scripts/build-release.sh` |
| APK للتجربة المباشرة | ✅ نفس التوقيع؛ نسخة تاج جُرِّبت على الجوال (R8) وتصل للإنتاج | `app/build/outputs/apk/*/release/` |
| أيقونة التطبيق | ✅ لكل نكهة: خلفية بلون الهوية + اسم المنصة وتحته driver (خط Alexandria، OFL) — فى التطبيق نفسه (أيقونة متكيّفة) وفى المتجر 512×512 | `graphics/<brand>-icon-512.png` + `app/src/<brand>/res/mipmap-*` |
| صورة العرض 1024×500 | ✅ منيورا + تاج | `graphics/*-feature-graphic.png` |
| لقطات الهاتف 1080×1920 | ✅ منيورا 5 · تاج 4 (بالجنيه وعناوين القاهرة) | `graphics/*-phone-*.png` |
| صفحة طلب حذف الحساب على الويب | ✅ مبنية ومُختبَرة (باك اند) | `{backend}/driver/account-deletion` — **تحتاج نشر الباك اند** |
| نصوص المتجر، أمان البيانات، الأذونات، التصنيف، وصول المراجعين، قسم سياسة الخصوصية | ✅ | ملفات هذا المجلد |

### 🔴 ما يبقى عليك (لا يمكن فعله من الكود)
1. **احفظ نسخة احتياطية من مجلد `~/driver-signing/`** (الملفان معاً) فى مكان آمن خارج هذا الجهاز. ضياعه مع Play App Signing يعنى طلب «إعادة تعيين مفتاح الرفع» من Google — مزعج لكنه ممكن؛ بدون Play App Signing يعنى استحالة تحديث التطبيق إلى الأبد.
2. **نشر الباك اند**: `git pull && php artisan migrate && php artisan route:cache && php artisan config:cache` على السيرفرين.
3. **سياسة الخصوصية** (الحاجب الحرج أدناه): ألحِق نص `privacy-policy-driver-section.md` بصفحة الخصوصية من لوحة الأدمن، ثم ضع رابطها فى `app.privacy_policy_url` على كل سيرفر.
4. **🔴 تطبيق تاج بلا مشروع Firebase إطلاقاً** (`app/src/taaj/` لا يحوى `google-services.json`): بدونه **لا دخول برمز الجوال ولا إشعارات عروض** على تاج — التطبيق يعمل بكلمة المرور فقط ولا تصله الطلبات بالدفع. **لا ترفع تاج قبل** إنشاء مشروع Firebase له (Phone Auth + FCM) ووضع ملفه فى `app/src/taaj/google-services.json`، وضبط `DRIVER_FIREBASE_PROJECT_ID`/`DRIVER_FCM_*` على سيرفر تاج. ⚠️ الباك اند يقرأ مشروع Firebase **واحداً** لكل سيرفر، وهذا يناسب الفصل (سيرفر لكل منصة).
5. **Firebase منيورا**: أضِف بصمتَى مفتاح الرفع (أعلاه) الآن لتجربة الـAPK، ثم بصمتَى **Play App Signing** بعد أول رفع (خطوة 1 أدناه).
6. **أرقام اختبار Firebase** للمراجعين (`app-access-for-reviewers.md`).
7. **قرار مُتَّخذ:** صلاحية الموقع فى الخلفية **تبقى** (كتطبيقات جاهز وهنقرستيشن) ⇒ عبّئ تصريح «Background location» وارفع الفيديو حسب `permissions-declarations.md`.
8. مراجعة ناطق أصلى لنصوص الأوردو/البنغالية/الهندية (اختيارى للرفع الأول).

---

> هذا المجلد بيانات فقط — لا كود، لا Gradle، لا التزام (commit). كل ملف هنا جاهز للنسخ داخل Play Console. **راجعه بنفسك قبل النشر** ثم نفّذ الخطوات أدناه بالترتيب.
>
> التطبيق له نكهتان منفصلتان تماماً فى Play Console (حزمتان مختلفتان، بائعان مختلفان إن أردت):

| | Meniura (السعودية) | Taaj (مصر) |
|---|---|---|
| Application ID | `app.qrmenu.driver.meniura` | `app.qrmenu.driver.taaj` |
| الباك اند | `https://app.meniura.com` | `https://app.taaj.me` |
| الموقع | meniura.com | taaj.me |
| الدعم | support@meniura.com | support@taaj.me |
| واتساب | 966508005004 | 201552511417 |

---

## 🔴 حاجب حرج قبل أى شىء — سياسة الخصوصية غائبة على السيرفرين

`app.privacy_policy_url` (إعدادات الأدمن ← إعدادات التطبيق) **قيمته `null` على منيورا وتاج معاً** (مُتحقَّق من الكود: `AppSettings.php` يقرأه، ولا شىء يضبطه افتراضياً). Google Play **يرفض** أى تطبيق يجمع بيانات حساب بلا رابط سياسة خصوصية حىّ يمكن الوصول إليه **من داخل التطبيق نفسه**. التطبيق يقرأ هذا الرابط من `GET /api/v1/driver/branding` (حقل `privacy_url` فى `DriverAppController::branding`) ويعرضه فى شاشة الحساب — **فطالما الحقل فارغ فى الأدمن، لا رابط أصلاً فى التطبيق المنشور، ولا حتى مراجعة Google الأولى ستمرّ**.

**إجراء المالك (إلزامى قبل الرفع):**
1. انشر صفحة سياسة الخصوصية المُحدَّثة بقسم السائقين (`play-store/privacy-policy-driver-section.md` فى هذا المجلد يعطيك النص الجاهز للإلحاق بالسياسة الحالية للمنصة — السياسة الحالية على الإنتاج لا تذكر السائقين ولا الموقع إطلاقاً).
2. من لوحة الأدمن (`/admin`) على **كل سيرفر على حدة**: إعدادات ← إعدادات التطبيق ← رابط سياسة الخصوصية (`app.privacy_policy_url`) → الصق رابط الصفحة المنشورة لتلك المنصة.
3. تحقّق: افتح `https://app.meniura.com/api/v1/driver/branding` (وكذا لـtaaj) وتأكد أن `privacy_url` لم يعد `null`.

---

## ترتيب النشر

### 0) قبل Play Console — على السيرفرين
- [ ] **رابط سياسة الخصوصية** مضبوط (أعلاه) — لكل منصة على حدة.
- [ ] رابط الشروط (`terms_url`) ورابط الدعم (`help_url`) مضبوطة إن وُجدت (اختيارية لكن تحسّن ثقة المراجع).
- [ ] `support_whatsapp`/`support_email` مضبوطان (يظهران فى شاشة الحساب).
- [ ] **صفحة طلب حذف الحساب على الويب** (`{backend}/driver/account-deletion`) منشورة ويمكن الوصول إليها بلا تسجيل دخول فى التطبيق — Google يتطلّب مساراً **خارج التطبيق أيضاً** لحذف حساب لمن أزال التطبيق. تحقّق: `curl -I https://app.meniura.com/driver/account-deletion` و`https://app.taaj.me/driver/account-deletion` ⇒ 200.
- [ ] Cron يشغّل `driver:purge-old-breadcrumbs` فعلياً (يُذكر فى إجابات Data Safety كسياسة احتفاظ حقيقية لا نظرية) — تحقّق من `app/Console/Kernel.php` أو الجدولة على السيرفر.

### 1) Firebase — **حرج قبل أول رفع موقَّع**
Google Play Signing (الموقّع الذى يستخدمه Google لتوقيع الحزمة النهائية) يُنتج بصمة **مختلفة** عن مفتاح رفع التطوير. إن لم تُضِف بصمة توقيع Play الجديدة لمشروع Firebase، **يتعطّل Firebase Phone Auth (OTP) بعد التنزيل من المتجر مباشرة** — كل محاولة دخول تفشل بصمت (reCAPTCHA/SafetyNet يرفضان التطبيق).
- [ ] فعّل Play App Signing عند أول رفع AAB (خطوة 3 أدناه) واحصل على **SHA-1 وSHA-256** من Play Console ← Release ← Setup ← App integrity.
- [ ] أضِف الاثنين إلى **مشروع Firebase الصحيح لكل نكهة** (مشروع منيورا لتطبيق منيورا، ومشروع تاج لتطبيق تاج إن كانا مشروعين منفصلين — تحقّق من `google-services.json` لكل نكهة) ← Project settings ← أضف بصمة SHA لتطبيق Android.
- [ ] بعد الإضافة انتظر بضع دقائق ثم اختبر تسجيل دخول حقيقى (OTP) على نسخة **من المتجر** (ليست debug) قبل أى إعلان تسويقى.

### 2) إنشاء التطبيق فى Play Console (لكل نكهة على حدة، حساب مطوّر واحد أو منفصل حسب رغبة المالك)
- [ ] اسم التطبيق، اللغة الافتراضية (العربية)، نوع (تطبيق)، مجانى.
- [ ] الصق نصوص القوائم من `listing-meniura.md` / `listing-taaj.md`.
- [ ] ارفع الصور من `graphics/`: `<brand>-icon-512.png`، و`<brand>-feature-graphic.png`، و`<brand>-phone-*.png` (من تشغيل حقيقى على S25، بلا بيانات عملاء حقيقية).

### 3) الحزمة والتوقيع
- [ ] الحزم مبنية فعلاً؛ لإعادة بنائها: `scripts/build-release.sh [meniura|taaj|all]` (يقرأ `~/driver-signing/signing.env`). ارفع ملف `.aab` لا `.apk`.
- [ ] ارفعها فى مسار الاختبار الداخلى (Internal testing) أولاً، **ليس** الإنتاج مباشرة.
- [ ] فعّل Play App Signing إن لم يكن مفعّلاً (خطوة 1 أعلاه تعتمد على هذا).

### 4) تصنيف المحتوى (Content rating)
- [ ] عبّئ استبيان IARC كما فى `content-rating.md`. لا محتوى للبالغين، لا عنف، لا مقامرة.

### 5) الجمهور المستهدف (Target audience)
- [ ] كما فى `target-audience.md`: 18+ فقط، **ليس** موجَّهاً للأطفال (`false` على "Is your app designed for children?").

### 6) خصوصية البيانات (Data safety form)
- [ ] عبّئ الاستمارة **حرفياً** من `data-safety.md` — كل نوع بيانات، الغرض، إجباري/اختياري، مُشفَّر أثناء النقل (نعم، HTTPS)، وجود مسار حذف.

### 7) إعلانات الأذونات (Permissions declarations)
- [ ] `ACCESS_BACKGROUND_LOCATION`: عبّئ استمارة "Background location" فى Play Console كما فى `permissions-declarations.md`، وارفع فيديو العرض (سكربت اللقطات فى نفس الملف).
- [ ] `FOREGROUND_SERVICE_LOCATION` + `USE_FULL_SCREEN_INTENT`: نفس الملف.

### 8) الوصول للمراجعين (App access)
- [ ] اتبع `app-access-for-reviewers.md` لإنشاء حساب سائق تجريبى برقم Firebase Test Number يعمل بلا OTP حقيقى.

### 9) قبل الإرسال للمراجعة
- [ ] سياسة الخصوصية منشورة **وتذكر السائقين والموقع صراحة** (البند 🔴 أعلاه).
- [ ] صفحة حذف الحساب على الويب تعمل.
- [ ] رابط سياسة الخصوصية يظهر فعلياً داخل شاشة "الحساب" فى نسخة من المتجر (وليس فقط فى الكود).
- [ ] اختبار OTP حقيقى بعد إضافة بصمة Play Signing لـFirebase (خطوة 1).
- [ ] لا ادّعاءات فى نص القائمة تتجاوز ما يفعله التطبيق فعلياً (رُوجعت فى `listing-*.md`).

### 10) بعد الموافقة على الاختبار الداخلى
- [ ] رقِّ لـ Closed/Open testing إن رغبت، ثم Production — تدرّجى وليس دفعة واحدة لتقليل مخاطر الرفض على جمهور حقيقى.

---

## ملاحظة تقنية (لماذا هذه الحزمة مبنية هكذا)
كل رقم/نص فى الملفات التالية مُشتقّ من قراءة الكود الفعلى (`AndroidManifest.xml`، `core/network` DTOs، `core/location`/`core/notifications`، `gradle/libs.versions.toml`) والباك اند (`app/Http/Controllers/Api/V1/Driver/*`, migrations, `PurgeOldBreadcrumbs`) — لا افتراضات تسويقية. أى شىء غير مؤكَّد فى الكود ذُكر كحاجب (⚠️) بدل التخمين.
