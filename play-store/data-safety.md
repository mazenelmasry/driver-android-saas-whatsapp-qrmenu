# استمارة Data Safety — تطبيق السائقين (Meniura Driver / Taaj Driver)

> إجابات جاهزة للنسخ فى Play Console ← App content ← Data safety. كل بند مُستنَد لموضع فى الكود. النكهتان (منيورا/تاج) متطابقتان فى هذا البند — نفس شكل البيانات، باك اند مختلف فقط.

## أسئلة عامة (Security practices)

| السؤال | الإجابة |
|---|---|
| هل البيانات مُشفَّرة أثناء النقل؟ | **نعم** — كل اتصال بالباك اند عبر HTTPS (`core/network`، `API_BASE_URL` = `https://app.meniura.com` / `https://app.taaj.me`). لا نداء HTTP خام. |
| هل يوفّر التطبيق مساراً لطلب حذف البيانات؟ | **نعم** — من داخل التطبيق (`POST /driver/account/deletion-request`، `DriverAccountController::requestDeletion`) وأيضاً من صفحة ويب عامة `{backend}/driver/account-deletion` بلا حاجة لتثبيت التطبيق. الحذف **مُراجَع من الأدمن لا فورياً** (السائق قد يحمل نقد مطعم غير مسوّى؛ انظر migration `driver_deletion_requests`). |
| هل يلتزم التطبيق بـFamily Policy؟ | غير منطبق — التطبيق ليس موجَّهاً للأطفال (انظر `target-audience.md`). |

---

## أنواع البيانات المجموعة/المُشارَكة

### 1) الموقع الدقيق (Precise location) — **مُجمَّع، مُشارَك**
- **أين فى الكود:** `core/location` (`ACCESS_FINE_LOCATION`/`ACCESS_BACKGROUND_LOCATION`)، يُرفَع عبر `LocationBatchRequest`/`BreadcrumbsRequest` (`core/network/.../OrderDtos.kt`) إلى `driver/availability/location` وأثناء الرحلة.
- **الغرض:** وظيفة التطبيق (App functionality) — إسناد طلبات توصيل قريبة، وعرض مسار رحلة التوصيل للمطعم، وتسوية نزاعات "لم يصل السائق" عبر `trip_breadcrumbs`.
- **إلزامى أم اختيارى؟** اختيارى فعلياً من منظور الاستخدام (السائق يختار "متاح")، لكن **إلزامى وظيفياً** لتلقّى أى طلب — بدونه لا يعمل جوهر التطبيق. صرّح به كـ"Required" فى Play (الوظيفة الأساسية تعتمد عليه).
- **يُشارَك مع طرف ثالث؟** **نعم** — مع المطعم صاحب الطلب فقط (يرى مسار رحلة التوصيل فى تفاصيل الطلب). العميل لا يرى موقع السائق. لا يُشارَك مع أى معلن أو طرف تسويقى.
- **مُحذوف/مُجهَّل بعد مدة؟** **نعم** — `trip_breadcrumbs` يُحذَف تلقائياً بعد 30 يوماً (`php artisan driver:purge-old-breadcrumbs`، مجدول). آخر موقع حىّ على صف السائق يُستبدَل باستمرار ولا يُراكَم تاريخياً.
- **يجمعه التطبيق فقط عند "متاح":** لا تتبّع إطلاقاً فى الوضع غير المتاح (مُثبَّت فى نص الإفصاح داخل التطبيق نفسه، `location_rationale_body`).

### 2) رقم الهاتف (Phone number) — **مُجمَّع، غير مُشارَك تسويقياً**
- **أين:** `RequestOtpRequest`/`VerifyOtpRequest`/`LoginRequest` (`AuthDtos.kt`)، ومُخزَّن E.164 على `DriverDto.phone`.
- **الغرض:** المصادقة الأساسية للحساب (لا بريد إلكترونى فى هذا التطبيق) + معرّف يربط السائق بالمطعم.
- **إلزامى:** نعم — لا تسجيل دخول بدونه.
- **يُشارَك:** يُرسَل لـFirebase (Google) لأداء التحقق من OTP (`firebase_token` فى `VerifyOtpRequest`) — صرّح بـ"Shared with Firebase Authentication (service provider)".
- **حذف:** ضمن طلب حذف الحساب.

### 3) الاسم (Name) — **مُجمَّع**
- **أين:** `DriverDto.name`. يُعيّنه السائق (أو دعوة المطعم) — ليس مأخوذاً من رقم الجوال تلقائياً (قرار مُصلَح سابقاً فى الباك اند).
- **الغرض:** وظيفة التطبيق — يظهر للمطعم والعميل كاسم من يسلّم الطلب.
- **إلزامى:** نعم.

### 4) معلومات مالية — النقد المُحصَّل (Financial info) — **مُجمَّع**
- **أين:** `DeliveredRequest.cashCollected` + `LedgerSummaryDto` (المحفظة/الدفتر).
- **الغرض:** وظيفة التطبيق فقط — تسوية حساب السائق مع المطعم (كم نقداً يحمل). **لا بيانات بطاقة دفع فى هذا التطبيق إطلاقاً** — لا يعالج مدفوعات عملاء.
- **إلزامى:** نعم لسائقين يقبضون نقداً.
- **يُشارَك:** مع المطعم فقط (صاحب المبلغ).

### 5) معرّفات الجهاز (Device / other IDs) — **مُجمَّع**
- **أين:** `DeviceTokenRequest.deviceToken` (توكن FCM، `core/push`) و`LoginRequest.deviceName`/`appVersion`.
- **الغرض:** وظيفة التطبيق (توصيل إشعارات طلبات التوصيل) + تشخيص/دعم فنى (معرفة إصدار التطبيق).
- **يُشارَك:** توكن FCM يُرسَل عبر Firebase Cloud Messaging (Google) لتوصيل الإشعار — خدمة معالجة لا تسويق.

### 6) بيانات الاستخدام / السجلات (App activity, App info & performance) — **غير مُجمَّع من طرف ثالث**
- **لا Firebase Analytics ولا Crashlytics ولا Firebase Performance مُفعَّلة فعلياً.** موجودة فقط كإدخالات فى `gradle/libs.versions.toml` (Sprint 12.C) **ولم تُطبَّق (apply) على أى `build.gradle.kts`** — تم التحقق بالبحث: لا `id("...crashlytics")`/`id("...perf")` فى أى ملف بناء. صرّح بـ**"No"** لجمع بيانات الأعطال/الأداء عبر مزوّد تحليلات تابع لجهة ثالثة، ما لم يُفعَّلا قبل النشر (فى هذه الحالة يجب تحديث هذه الاستمارة).
- Firebase الأساسى المُستخدَم فعلياً: **Authentication** (OTP) و**Cloud Messaging** (الإشعارات) فقط.

### 7) بيانات لا يجمعها التطبيق إطلاقاً
لا بريد إلكترونى، لا جهات اتصال، لا صور/كاميرا، لا ملفات، لا سجل مكالمات/رسائل، لا معرّف إعلانى، لا بيانات صحة/لياقة.

---

## ملخّص جدول Data Safety (للنسخ السريع فى الاستمارة)

| نوع البيانات | مُجمَّع | مُشارَك | الغرض | إلزامى |
|---|---|---|---|---|
| Precise location | ✅ | ✅ (المطعم) | App functionality | Required |
| Phone number | ✅ | ✅ (Firebase Auth كمزوّد خدمة) | Account management, Authentication | Required |
| Name | ✅ | ✅ (المطعم) | App functionality | Required |
| Financial info (cash collected) | ✅ | ✅ (المطعم) | App functionality | Required (نقداً) |
| Device or other IDs (FCM token, device name, app version) | ✅ | ✅ (Firebase Cloud Messaging) | App functionality | Required |
| App activity / crash logs / analytics | ❌ | ❌ | — | — |

**نوع الأمان العام:** بيانات مُشفَّرة أثناء النقل (نعم)، مسار حذف بيانات متاح (نعم).
