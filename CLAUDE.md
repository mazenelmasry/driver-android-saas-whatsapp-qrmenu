# CLAUDE.md — تطبيق السائقين (Android أصلى — Kotlin + Jetpack Compose)

> **لأى وكيل ذكاء اصطناعى يعمل فى هذا الريبو.** يُحمَّل تلقائياً فى كل جلسة. اقرأه كاملاً قبل أول سطر كود.
> المشروع يُبنى **بالكامل بوكلاء برمجة بلا مبرمج بشرى** — لذلك القواعد هنا ليست تفضيلات، بل هى الحارس الوحيد. خالفها = خطأ لا يراه أحد إلا بعد أن يصل لسائق فى الشارع.

---

## ⛔ القواعد الحاكمة (اقرأها أولاً — لا استثناء)

### 🌐 لغة التواصل
- **🔴 كل كلمة موجّهة للمستخدم تكون بالعربية — بلا أى استثناء.** الرد النهائى، ورسائل المتابعة القصيرة بين الأدوات («أبنى الآن…»)، والأسئلة، والجداول، والشرح، وملخّصات ما تمّ، وأسباب القرارات.
- المسموح بالإنجليزية فقط: أسماء الملفات والدوال والأوامر والمسارات داخل `code`، والكود نفسه وتعليقاته، ورسائل الـcommits.
- بدء رسالة بالإنجليزية أو كتابة فقرة إنجليزية = مخالفة صريحة لتعليمات صاحب المشروع.

### 🤖 الوكلاء الفرعيون
- إن شغّل قائد الجلسة وكلاء فرعيين: **Sonnet 5 حصراً** (`model: "sonnet"`) **وبحد أقصى 3 وكلاء متوازين**. لا تفترض نطاقاً أكبر حتى لو كان `ultracode` مفعّلاً.
- **كل وكيل فى وحدة Gradle مختلفة** (وكيلان فى نفس الوحدة يُفشلان اختبارات بعضهما).
- **ممنوع على الوكلاء الفرعيين:** `commit`، إنشاء فرع، `git stash/checkout/reset`، تعديل `CLAUDE.md`، تعديل الباك اند بلا تكليف صريح. قائد الجلسة وحده يلتزم ويدفع.
- راجع كل حكم «ليس خللاً» يصدره وكيل بنفسك قبل قبوله.
- استخدم أداة `Agent` مباشرة، لا `Workflow`، إلا بطلب صريح من صاحب المشروع.

### 🌿 Git
- **🔴 لا فروع — العمل على `main` مباشرةً دائماً.** حتى لو طُلبت «مراجعة قبل الالتزام»: اترك التغيير غير مُلتزَم على `main` حتى يوافق صاحب المشروع.
- **🔴 لا CI ولا فحوصات على GitHub.** كل التحقق **محلياً وعلى جهاز حقيقى** قبل أى `commit` (انظر «بروتوكول التحقق»). لا تُنشئ `.github/workflows` — قرار صاحب المشروع: الفحص على الجهاز لا على GitHub حتى لا تتراكم مخاطر صامتة.
- **لا `push` لـcommit لم يمرّ ببروتوكول التحقق كاملاً.** الدفع بعد إخبار صاحب المشروع بنتيجة التحقق.
- `CLAUDE.md` و`.claude/` **متتبَّعان فى Git** فى هذا الريبو (لا خادم إنتاج يسحبه؛ الهدف أن يقرأه كل جهاز تطوير). ⚠️ هذا عكس ريبو الباك اند حيث `CLAUDE.md` متجاهل عمداً — لا تخلط بينهما.
- رسائل الـcommit بالإنجليزية بصيغة `type(scope): what` (`feat(trip): …`, `fix(location): …`).

### 🚫 ممنوعات مطلقة
- لا `hardcode` لأى دومين أو مفتاح API أو معرّف حساب فى الكود. الدومين من الـflavor، والأسرار من `local.properties`/متغيرات البيئة.
- لا تطبيق باسم كل مطعم. **تطبيق واحد لكل منصة** (منيورا / تاج)، والمطعم يظهر داخل الطلب.
- لا كتابة موقع السائق فى MySQL لكل نبضة. **Redis GEO فقط** (انظر «الموقع»).
- لا `CircularProgressIndicator` فى وسط الشاشة، لا `tween()` لحركة يقودها المستخدم، ولا شاشة بلا حالات (فارغ/خطأ/تحميل/بلا اتصال).
- لا نصّ حرفى داخل Composable — كل نصّ فى `strings.xml` وبكل اللغات المدعومة.
- لا تعديل عقد API فى طرف واحد. **الريبوان معاً** + تحديث `openapi/driver.v1.yaml` + إعادة توليد مرآة العقد.
- لا `migrate:fresh`/`refresh`/`reset`/`db:wipe` فى الباك اند أبداً (قاعدة صاحب المشروع).

---

## 🧭 ما هذا المشروع — الرؤية والمرحلة الحالية

### الفكرة
تطبيق أندرويد **للسائقين** يعمل مع منصة المنيو الرقمى/نقطة البيع (منيورا / تاج). فى المرحلة الأولى: **المطعم يوظّف سائقيه بنفسه** ويديرهم من لوحته، والسائق يستلم طلبات التوصيل ويوصّلها ويعلّم التسليم — بلا أى مال يمرّ بالمنصة، وبلا ترخيص نقل. فى مراحل لاحقة (بقرار منفصل مبنى على أرقام): محرك توزيع ذكى، ثم **أسطول سائقين تابع للمنصة** يخدم كل المطاعم المشتركة.

### القرار الاستراتيجى المُجمَّد (اتُّخذ 2026-09-21)
```
المرحلة ١  ← أسطول المطعم (سائق المطعم نفسه)             ← نبنيها الآن، أولوية قصوى
المرحلة ٢  ← محرك التوزيع المتقدّم (موجات، ETA، تجميع)     ← بعد إطلاق ١ واستقرارها
المرحلة ٣  ← الجودة والمال للمطعم (نقد السائق، تقييم، إثبات) ← مع/بعد ٢
المرحلة ٤  ← أسطول المنصة (سائقون تابعون لمنيورا)          ← فقط إن تجاوز مقياس no_driver_available العتبة
```
- **لماذا هذا الترتيب:** المرحلة ١ تُباع اليوم كميزة خطة، لا تحتاج ترخيصاً ولا محفظة ولا تسويات، وتبنى **نفس** البنية التحتية التى تحتاجها المراحل التالية.
- **🔑 المرحلة ١ تقيس سوق المرحلة ٤ مجاناً:** كل مرة يحين فيها وقت إرسال طلب توصيل **ولا سائق متاح** فى أسطول المطعم يُسجَّل حدث `no_driver_available`. هذا الرقم هو خطة عمل المرحلة ٤ وعتبة إطلاقها (**≥15٪ من طلبات التوصيل فى حىّ واحد + طلب صريح من 5 مطاعم**). لا تقرّر المرحلة ٤ بالانطباع.
- **مفهوم «الأسطول» يُبنى من اليوم الأول** (`fleets` بنوع `restaurant` الآن، و`platform` لاحقاً) حتى تكون المرحلة ٤ إضافةَ أسطولٍ لا إعادة بناء.

### الأولوية الآن
> **«أى مطعم يريد توظيف سائقيه — أشغّله بسرعة.»** كل قرار فى المرحلة ١ يُقاس بهذا: هل يقرّب إطلاقاً يعمل عند مطعم حقيقى خلال أسابيع؟ ما لا يقرّبه يُؤجَّل.

---

## 📂 خريطة المشاريع (أربعة ريبوهات مستقلة)

| المشروع | المسار المحلى | الدور |
|---|---|---|
| **الباك اند** (Laravel 12 + Filament v5 + Livewire v4) | `C:\laragon\www\filamentv4-saas-whatsapp-qrmenu` | لوحات الأدمن/الشركة + REST API. **مصدر الحقيقة لكل عقد `/api/v1/driver/*`** |
| الفرونت اند (Next.js 16) | `C:\laragon\www\qrmenu-nextjs-saas-whatsapp` | المنيو العام + صفحة تتبّع الطلب للعميل (`app/[companySlug]/order/[orderNumber]`) |
| نقطة البيع (Android — Kotlin/Compose) | `C:\laragon\www\pos-android-native-saas-whatsapp-qrmenu` | **المرجع الذى تُنسخ منه الأنماط.** ريبو `git@github.com:mazenelmasry/pos-saas-whatsapp-qrmenu.git` |
| **تطبيق السائقين (هذا الريبو)** | `C:\laragon\www\driver-android-saas-whatsapp-qrmenu` | تطبيق أندرويد للسائق، يستهلك `/api/v1/driver/*` |

- **منصتان (multi-brand):** نفس الباك اند يخدم `app.taaj.me` و`app.meniura.com`. الدومين من الـflavor فقط.
- **قاعدة الريبوين:** أى ميزة تمتد عبر الباك اند والتطبيق تُعدَّل فى الريبوين معاً، وcommit/push **منفصل لكل ريبو**.
- **اقرأ `CLAUDE.md` فى ريبو الباك اند** قبل أى تعديل هناك (قواعد ملزمة: لا `migrate:fresh`، `route:cache` بعد النشر، `npm run build` + `git add public/build` قبل الدفع لأى تغيير واجهة، New-Feature Checklist، إلخ).
- **لا تعتمد على ريبو POS كوحدة مشتركة** — انسخ الأنماط ملفاً ملفاً. وحدة مشتركة يعدّلها وكيل من أجل السائق تكسر الكاشير عند عملاء يدفعون. نطاق الضرر هو المعيار حين لا يوجد مراجع بشرى.

---

## ✅ ما هو موجود فعلاً فى الباك اند (لا تُعِد بناءه)

اكتُشف بالفحص 2026-09-21 — **المرحلة ١ تبنى فوق هذا**:

| الموجود | أين | ملاحظة |
|---|---|---|
| دور `delivery_driver` (ViewAny/View/Update على Order/Customer/DeliveryZone) | `database/seeders/PermissionSeeder.php` | السائق = `User` تابع للشركة يحمل هذا الدور |
| شاشة «سائقو التوصيل» فى لوحة الشركة | `app/Filament/Company/Resources/DeliveryDrivers/*` | إنشاء سائق: اسم/جوال/بريد/كلمة مرور/فرع/نشط. مبوّبة بـ`PlanFeature::DeliveryAreas` |
| إجراء «تعيين/إعادة تعيين سائق» على الطلب | `ViewOrder.php` (`assign_driver`) + `CompanyOrderActionController::assignDriver` | يكتب `orders.delivery_driver_id` (FK → `users`) |
| إشعار السائق عند التعيين | `app/Jobs/SendDriverAssignedNotification.php` | OneSignal بـ`externalIds: ["user_{id}"]` + إشعار قاعدة بيانات |
| `OrderStatus::OutForDelivery` / `Delivered` + `out_for_delivery_at` / `delivered_at` | `app/Enums/OrderStatus.php`, `app/Models/Order.php` | الانتقال `Ready → OutForDelivery → Delivered` عبر `Order::transitionTo()` **حصراً** |
| عرض السائق للعميل (اسم + جوال) | `Api\OrderResource::formatDeliveryDriver` | مشروط بإعداد الشركة `show_driver_contact_to_customer` |
| نمط التوكن المخصّص (لا Sanctum) | `PosStaffToken` + `PosStaffAuth` + `PosBroadcastingController` | **انسخه** إلى `DriverToken` / `DriverAuth` / `DriverBroadcastingController` |
| هوية المنصة لشاشة الدخول | `GET /api/v1/pos/branding` (عام) | يُعاد استخدامه كما هو |
| Redis (predis) + Reverb (بثّ فورى) + OneSignal | `.env`, `OneSignalService` | جاهز للموقع الحىّ والإشعارات |
| مناطق التوصيل + `delivery_address` + إحداثيات العميل | `DeliveryZone`, `orders.delivery_address` | |
| زمن تحضير الصنف | `products.preparation_time` (بالدقائق) | أساس توقيت الإرسال |

**ما ليس موجوداً (نبنيه):** مصادقة السائق عبر التطبيق، endpoints السائق، توفّر السائق (online/offline)، الموقع الحىّ، طرق الربط الثلاث وتوقيت الإرسال، أوامر الرحلة (استلمت/سلّمت)، سجلّ أحداث التوصيل، مقياس `no_driver_available`، مفهوم الأسطول.

---

## 🏗️ القرارات المعمارية المُجمَّدة

| القرار | القيمة | لماذا |
|---|---|---|
| اللغة/الإطار | **Kotlin + Jetpack Compose (أصلى)** | الموقع فى الخلفية هو جوهر التطبيق ولا يجوز وضعه خلف إضافة لا نملكها؛ وتطبيق POS الأصلى مرجع جاهز؛ وFlutter هُجر فعلاً فى POS |
| الريبو | مستقل، أنماط منسوخة من POS | نطاق الضرر |
| الحزمة | `app.qrmenu.driver` + لاحقة لكل نكهة: `.meniura` / `.taaj` | درس POS (NEW-B-01): نكهتان بنفس `applicationId` تتشاركان التخزين ⇒ تسريب جلسة بين مستأجرين |
| النكهات | `meniura` → `https://app.meniura.com`، `taaj` → `https://app.taaj.me` | نسخ `Flavor.kt` من POS مع إعادة تسمية `pos.` → `driver.` |
| Debug base URL | `driver.debugBaseUrl` فى `local.properties` (افتراضه `http://10.0.2.2:8000`؛ على جهاز حقيقى `http://127.0.0.1:8000` عبر `adb reverse`) | بلا ملفات متتبَّعة |
| minSdk / target / compile | **26 / 35 / 35** | كما POS؛ 26 لأجل Foreground Service APIs |
| المصادقة | **`DriverToken` Bearer مخصّص (لا Sanctum)** عبر `driver.auth` | جلسة جهاز واحدة، إبطال فورى، نفس نمط POS المُجرَّب |
| هوية السائق | **`User` بدور `delivery_driver`** (كما فى Filament اليوم) + جدول `driver_profiles` + جدول `fleets` | صفر تغيير على الشاشة القائمة؛ الأسطول من اليوم الأول |
| بوّابة الخطة | **`PlanFeature::DeliveryAreas`** (نفسها التى تبوّب شاشة السائقين) — ⚠️ افتراضى، انظر «قرارات معلّقة» | لا ميزة جديدة حتى يُقرَّر بيعها منفصلة |
| الإشعارات | **OneSignal** (موجود ومُوصَّل؛ `external_id = user_{id}`) — ⚠️ افتراضى | صفر عمل خلفى؛ FCM مباشر لاحقاً إن تأخّر التسليم |
| الموقع الحىّ | **Redis GEO** (`GEOADD driver:loc:{company}`) + `last_seen_at` فى MySQL كل دقيقة على الأكثر | 1.2 مليون كتابة يومياً لـ100 سائق تقتل MySQL |
| الخرائط | **Google Maps SDK** بمفتاح **المنصة** (مقيّد ببصمة التطبيق) + **ملاحة خارجية** (نيّة لخرائط جوجل/Waze) | لا ملاحة داخل التطبيق = معظم فاتورة Directions تسقط |
| اللغات | **ar, en, ur (أردو، RTL), bn (بنغالى)** — واقتراح **hi (هندى)** | أغلب السائقين فى السعودية يتحدثونها. الأرقام لاتينية دائماً |
| Offline-first | طابور Room للأوامر (`driver_actions_outbox`) يُرفع عند عودة الشبكة، بمفتاح idempotency | السائق فى قبو/مصعد/منطقة سيئة — نسخة `OrderUploadWorker` |
| الأخطاء | Sentry (`driver.sentryDsn` فى `local.properties`) | بدونه تسمع «ما اشتغل» بلا دليل |
| iOS | **ليس الآن.** حين يأتى: Compose Multiplatform أو SwiftUI | لا يغيّر شيئاً فى هذا الريبو |

---

## ⚙️ إعدادات التوصيل لكل فرع (طلب صاحب المشروع 2026-09-21)

> كل فرع يقرّر بنفسه **كيف يُربط السائق بالطلب** و**متى يُرسَل الطلب للسائقين**. الإعدادان على `branches` ويُحرَّران من `BranchForm` (قسم «التوصيل بالسائقين») ومن لوحة Next.js لاحقاً. **`App\Services\Delivery\DispatchPolicy` هو السطح الوحيد** الذى يقرأهما ويقرّر — لا تكتب `if ($branch->driver_assignment_mode === ...)` فى أى كنترولر.

### أ) طريقة الربط — `branches.driver_assignment_mode`
| القيمة | السلوك | ملاحظات |
|---|---|---|
| `manual` | المطعم يختار السائق **لكل طلب** من اللوحة (الإجراء الموجود `assign_driver`) | الافتراضى للمطاعم القائمة (يطابق السلوك الحالى تماماً) |
| `auto_nearest` | تلقائى: أقرب سائق **متاح** للفرع (Redis `GEOSEARCH`) يُعيَّن مباشرة ويُخطَر | تعادل المسافة ⇒ الأقلّ طلبات نشطة |
| `auto_least_loaded` | تلقائى: السائق المتاح **الأقل طلبات نشطة الآن**؛ التعادل ⇒ **الأكثر توصيلاً فى يوم العمل** (يوزّع الحمل ويكافئ النشط) | لا يحتاج موقعاً — يعمل حتى لو تعطّل GPS |
| `self_claim` | يُعرض الطلب فى «الطلبات المتاحة» لكل سائقى الفرع المتاحين، **وأول من يلتقطه يفوز** (ذرّى) | الأبسط للمطاعم الصغيرة (٢–٤ سائقين) |

- **الوضعان التلقائيان يسقطان على `self_claim` تلقائياً** إن لم يُوجد سائق متاح لحظة الإرسال (الطلب يبقى فى «المتاحة» + إشعار للمطعم «لا سائق متاح» + حدث `no_driver_available`). وبعد `driver_auto_assign_timeout_minutes` (افتراضه 5) بلا التقاط يُنبَّه المطعم مجدداً.
- **السائق المُعيَّن تلقائياً يستطيع الرفض** خلال `driver_accept_timeout_seconds` (افتراضه 45) ⇒ يُعاد التوزيع على التالى وتُسجَّل نسبة قبوله. بلا ردّ = رفض ضمنى.
- **الوضع اليدوى لا يتأثر بالتوقيت:** التعيين من اللوحة فورى دائماً ويُخطَر السائق فوراً.
- «الأكثر توصيلاً» = عدد `delivered` للسائق فى **يوم العمل** (`ReportPeriod::todayForBranch()` نفس تعريف POS)، وليس التاريخى — حتى لا يحتكر سائق قديم الطلبات كلها.

### ب) توقيت الإرسال — `branches.driver_dispatch_timing`
| القيمة | متى يظهر الطلب للسائق / يُعيَّن | الأنسب لـ |
|---|---|---|
| `on_new_order` | **فور** دخول طلب التوصيل (حتى قبل قبوله فى المطبخ) | مطعم يريد السائق فى المكان مبكراً (سائقون على الباب، أوقات ذروة) |
| `on_ready` | عند `Ready` (المطبخ ضغط «جاهز») | الافتراضى — لا انتظار للسائق ولا طعام يبرد |
| `by_item_prep_time` | بعد **مجموع** `preparation_time` لأصناف الطلب من لحظة التأكيد (للمطبخ التسلسلى) | مطبخ صغير يحضّر صنفاً صنفاً |
| `by_max_item_prep_time` | بعد **أعلى** `preparation_time` لأى صنف فى الطلب من لحظة التأكيد − `driver_dispatch_lead_minutes` (افتراضه 5) | المطبخ الموازى (الأكثر واقعية) — السائق يصل والطلب يكاد يكتمل |

- الحسابان الزمنيان يعتمدان على **`confirmed_at`** (لا `created_at`): طلب ينتظر القبول لا يُحسب زمنه. الأصناف بلا `preparation_time` تُعدّ بافتراض الفرع `driver_default_prep_minutes` (افتراضه 10).
- **الإرسال المؤقَّت يُنفَّذ بـjob مؤجَّل** (`DispatchOrderToDrivers` بـ`delay`) **ويُعاد التحقق عند التنفيذ**: الطلب ما زال حياً وتوصيلاً وبلا سائق؟ وإلا لا شىء. وإن صار `Ready` قبل الموعد المحسوب ⇒ يُرسَل فوراً (الجاهزية الفعلية تفوز على التقدير).
- **الطلبات المجدولة** (`scheduled_for`) تُحسب من موعدها لا من الآن.
- **`driver_show_before_ready`** (bool): إن كان التوقيت مبكراً، هل يرى السائق أزرار «استلمت» قبل `Ready`؟ **لا** — الزر معطّل حتى `Ready` مع عدّاد «جاهز خلال ~N د»؛ السائق يرى الطلب ويتحرّك، لكن الاستلام ينتظر المطبخ.
- كل قرار توقيت/ربط يُسجَّل فى `order_delivery_events` (`dispatch_scheduled{at, rule}`, `dispatch_fired`, `auto_assigned{driver, rule}`, `auto_assign_declined`, `no_driver_available`) — بلا هذا السجل لا تستطيع أن تجيب المطعم «لماذا ذهب الطلب لفلان؟».

### ج) إعدادات مكمّلة على `branches`
`driver_auto_assign_timeout_minutes` (5) · `driver_accept_timeout_seconds` (45) · `driver_dispatch_lead_minutes` (5) · `driver_default_prep_minutes` (10) · `driver_max_active_orders` (2 — سقف الطلبات المتزامنة لكل سائق) · `driver_show_before_ready` (true).

---

## 🧱 بنية المشروع

```
driver-android-saas-whatsapp-qrmenu/
├── CLAUDE.md                      ← هذا الملف (ذاكرة المشروع)
├── PLAN.md                        ← تفصيل المراحل ومعايير القبول (يُستخرج من هذا الملف)
├── openapi/driver.v1.yaml         ← نسخة مرآة من عقد الباك اند (المصدر فى ريبو الباك اند)
├── scripts/verify.sh              ← بروتوكول التحقق الكامل (بناء + اختبار + تثبيت + دخان)
├── build-logic/                   ← convention plugins منسوخة من POS (أعد التسمية pos→driver)
├── app/                           ← نكهتا meniura/taaj، Application، MainActivity، Hilt entry
├── core/
│   ├── common/                    ← Result، Money، Bidi/Latin digits، وقت
│   ├── model/                     ← الكيانات النقية (Order, Trip, Driver, DispatchOffer)
│   ├── network/                   ← Retrofit + interceptors + DTOs + مرآة العقد
│   ├── database/                  ← Room: outbox + cache للطلبات
│   ├── datastore/                 ← TokenStore (Encrypted) + LocaleManager + prefs
│   ├── designsystem/              ← الثيم + المكوّنات + الأيقونات
│   ├── ui/                        ← مكوّنات مشتركة (Skeleton، EmptyState، ErrorBanner، OfflineBanner)
│   ├── location/                  ← ForegroundService + FusedLocation + heartbeat
│   ├── sync/                      ← رفع الطابور + WorkManager + ConnectivityObserver
│   └── notifications/             ← OneSignal + قنوات الإشعار + شاشة التعيين/العرض
└── feature/
    ├── auth/                      ← دخول، اختيار فرع، اللغة
    ├── availability/              ← الشاشة الرئيسية: متاح/غير متاح + الطلبات المتاحة/طلباتى
    ├── trip/                      ← تفاصيل الطلب + مراحل الرحلة + الملاحة + التسليم
    ├── history/                   ← طلبات اليوم/عدد التوصيلات
    └── settings/                  ← اللغة، الجهاز، البطارية، الوثائق (لاحقاً)، الخروج
```

### الحزم والإصدارات (طابق POS لتفادى مفاجآت البناء)
```
agp 8.11.2 · kotlin 2.0.21 · ksp 2.0.21-1.0.28 · compose-bom 2024.12.01 · hilt 2.52
room 2.6.1 · retrofit 2.11.0 · okhttp 5.0.0-alpha.14 · kotlinx-serialization 1.7.3
coroutines 1.9.0 · workmanager 2.9.1 · datastore 1.1.1 · sentry-android 8.16.0
+ play-services-location (أحدث مستقر) · maps-compose + play-services-maps · onesignal (SDK 5.x)
```
- ⚠️ **لا ترفع `compose-bom`** — الحزم الأحدث تتطلّب AGP 9 (درس POS).
- ⚠️ **لا Firebase plugin** (`google-services`) فى المرحلة ١ — OneSignal يعمل بمُرسِله الافتراضى. إن قُرِّر FCM مباشر لاحقاً يُضاف عندها.

### الأنماط المنسوخة من POS (بالاسم — انسخها ثم كيّفها)
| ما تنسخه | من POS | ملاحظة |
|---|---|---|
| `Flavor.kt` + convention plugins | `build-logic/` | `pos.` → `driver.`، الحزمة `app.qrmenu.driver` |
| `NetworkModule` + interceptors (`Auth`, `Locale`, `Idempotency`, `AppVersion`, `DnsRetry`, `ServerTime`, `PosAuthenticator`) | `core/network` | baseUrl = `API_BASE_URL + "/api/v1/driver/"`. **`DnsRetryInterceptor` مهم على HONOR/Huawei** |
| `TokenStore` (EncryptedSharedPreferences) | `core/datastore` | جلسة واحدة؛ الخروج القسرى (401) = الخروج العادى |
| `LocaleManager` + `AppLocaleStore` (أرقام لاتينية `-u-nu-latn`) | `core/datastore` | وسّع `supported` إلى `ar,en,ur,bn(,hi)` |
| `OrderUploadWorker` + `SyncScheduler` + `ConnectivityObserver` | `core/sync` | يصير `DriverActionUploadWorker` |
| `pos-ui-standards` + `add-compose-screen` + `add-room-entity` + `wire-hilt-module` + `add-api-endpoint` + `update-claude-md` | `.claude/skills/` | انسخها إلى `.claude/skills/` هنا وعدّل ما يخص التابلت → الجوال |
| `BackendContractMirrorTest` | `core/network/src/test` | يقرأ `openapi/driver.v1.yaml` ويطابق DTOs |

---

## 🔌 عقد الـAPI للمرحلة ١ — `/api/v1/driver/*`

> **المصدر:** ريبو الباك اند (`routes/api.php` + `openapi/driver.v1.yaml`). هذا الملف يصف النيّة؛ العقد المولَّد هو الحَكَم. أى تغيير ⇒ الريبوان + إعادة توليد المرآة.

### المصادقة
- `POST driver/login` `{email, password, device_name, app_version}` → `{token, driver{id,name,phone,branch_id,branch_ids[]}, company{id,name,name_ar,currency}, branch{id,name,assignment_mode,dispatch_timing}, fleet{id,type}}`
  - يقبل فقط مستخدماً `is_active` يحمل دور `delivery_driver` فى شركة نشطة باشتراك نشط وخطة تحمل بوّابة الميزة.
  - **جلسة جهاز واحدة لكل سائق** (الدخول الجديد يُبطل القديم).
  - limiter `driver-login`: 10/دقيقة لكل IP + 5/دقيقة لكل (email+IP). **بلا قفل حساب صلب** (DoS).
- `POST driver/logout` · `GET driver/me` · `POST driver/broadcasting/auth` (قناة `private-driver.{userId}` + `private-branch.{branchId}` بقراءة فقط)
- Middleware `driver.auth` (`App\Http\Middleware\DriverAuth`): توكن → `is_active` → شركة نشطة → فرع نشط → اشتراك → بوّابة الخطة. أكواد 403: `account_inactive`, `company_inactive`, `subscription_expired`, `feature_not_in_plan`. يحقن `driver_user`, `driver_branch`, `driver_company`, `driver_token`.
- كل الردود تحمل `code` مع أى 4xx (التطبيق يترجم بالكود لا بالنص).

### التوفّر والموقع
- `PATCH driver/availability` `{online: bool}` → يكتب `driver_profiles.is_online` + `online_since` + حدث. الإطفاء مرفوض (409 `has_active_trip`) وبيد السائق طلب مُستلَم.
- `POST driver/location` `{lat, lng, accuracy, speed?, heading?, recorded_at}` (مجمَّع: مصفوفة نقاط) → Redis `GEOADD` + `HSET driver:last:{id}` + تحديث `last_seen_at` فى MySQL **بحد أقصى مرة/دقيقة**. يُقبل بينما `is_online` فقط (وإلا 409 `driver_offline`).
- **Heartbeat:** سائق بلا موقع لأكثر من 3 دقائق يُعلَّم `is_online=false` تلقائياً (أمر مجدول كل دقيقة `driver:sweep-stale`) + يُبلَّغ بإشعار «انقطع الاتصال».

### الطلبات
- `GET driver/orders/available` → طلبات **توصيل** حان وقت إرسالها حسب `dispatch_timing` فى فروع السائق، **بلا سائق مُعيَّن**، مرتّبة بـ`ready_at` ثم `confirmed_at`. تُرجَع فقط فى الوضع `self_claim` أو عند سقوط الوضع التلقائى إليه.
- `GET driver/orders/mine` → المُعيَّنة لى وغير النهائية (بما فيها **عرض تلقائى ينتظر ردّى** بحقل `offer{expires_at}`).
- `GET driver/orders/{id}` · `GET driver/orders/history?date=`
- `POST driver/orders/{id}/claim` → **ذرّى**: `UPDATE orders SET delivery_driver_id=?, driver_claimed_at=NOW() WHERE id=? AND delivery_driver_id IS NULL` — الخاسر يستلم 409 `already_claimed`. يرفض من تجاوز `driver_max_active_orders` (422 `too_many_active_orders`). يبثّ `DriverOrderAssigned`.
- `POST driver/orders/{id}/accept` / `POST driver/orders/{id}/decline` → للتعيين التلقائى فقط؛ `decline` يعيد التوزيع فوراً ويسجّل السبب.
- `POST driver/orders/{id}/release` → قبل الاستلام فقط؛ يصفّر التعيين + حدث `released` + إعادة توزيع.
- `POST driver/orders/{id}/picked-up` → `transitionTo(OutForDelivery, driverId)`. مرفوض إن لم يكن الطلب `Ready` (422 `order_not_ready`).
- `POST driver/orders/{id}/delivered` `{cash_collected?: decimal, note?}` → `transitionTo(Delivered, driverId)`؛ إن كان `payment_method=cash` و`payment_status=pending` ⇒ `payment_status=paid` + `delivery_cash_collected`. (OTP العميل فى المرحلة ٣.)
- `POST driver/orders/{id}/issue` `{code: customer_unreachable|wrong_address|customer_refused|accident|other, note?}` → حدث + إشعار للمطعم. **لا يغيّر حالة الطلب** — القرار للمطعم.
- **كل أمر رحلة يحمل `Idempotency-Key`** (uuid من الطابور المحلى) — التكرار يعيد نفس الرد 200 لا خطأ.

### شكل الطلب للسائق (`DriverOrderResource`)
`id, order_number, status, delivery_method, payment_method, payment_status, total, cash_to_collect, customer{name, phone}, delivery_address{text, lat, lng, notes}, branch{id, name, address, lat, lng, phone}, items[{name, quantity}] (ملخّص فقط — بلا أسعار)، notes, scheduled_for, confirmed_at, expected_ready_at, ready_at, out_for_delivery_at, delivered_at, driver_claimed_at, offer{expires_at}?, distance_km?`
- ⚠️ **لا تُرسل للسائق:** بريد العميل، الكوبونات، الخصومات، تفاصيل الدفع الإلكترونى، ملاحظات الموظفين الداخلية.

### الفورى (Reverb)
- `private-driver.{userId}`: `DriverOrderOffered` (تعيين تلقائى ينتظر القبول), `DriverOrderAssigned`, `DriverOrderUpdated` (حالة/إلغاء/جاهزية), `DriverForceOffline`.
- `private-branch.{branchId}` (قراءة): `DriverOrderAvailable` لوضع الالتقاط الذاتى.
- التطبيق يستطلع `orders/mine` + `orders/available` كل 20 ثانية كشبكة أمان (كما `wire:poll` فى الويب).
- الإشعار (OneSignal) هو ما يوقظ الجهاز؛ البثّ ما يحدّث الشاشة المفتوحة.

### ما يتغيّر فى الباك اند (المرحلة ١)
```
migrations:
  fleets                (id, company_id nullable, type enum[restaurant,platform], name, is_active)
  driver_profiles       (user_id unique, fleet_id, vehicle_type, plate, is_online, online_since,
                         last_seen_at, last_lat, last_lng, push_external_id, app_version)
  driver_tokens         (نسخة PosStaffToken: user_id, branch_id, token(hash), device_name,
                         app_version, app_platform, last_used_at, expires_at)
  order_delivery_events (order_id, driver_id nullable, branch_id, type, meta json, created_at)
                         types: dispatch_scheduled, dispatch_fired, auto_assigned, auto_assign_declined,
                                assigned, claimed, released, picked_up, delivered, issue, no_driver_available
  orders                + driver_claimed_at, driver_offer_expires_at, dispatch_due_at, delivery_cash_collected
  branches              + driver_assignment_mode enum[manual,auto_nearest,auto_least_loaded,self_claim] (manual)
                        + driver_dispatch_timing enum[on_new_order,on_ready,by_item_prep_time,by_max_item_prep_time] (on_ready)
                        + driver_auto_assign_timeout_minutes(5), driver_accept_timeout_seconds(45),
                          driver_dispatch_lead_minutes(5), driver_default_prep_minutes(10),
                          driver_max_active_orders(2), driver_show_before_ready(true)

enums:            DriverAssignmentMode, DriverDispatchTiming, OrderDeliveryEventType
models/services:  DriverToken, DriverProfile, Fleet, OrderDeliveryEvent, DriverAuthService,
                  DriverLocationService (Redis), Delivery\DispatchPolicy (السطح الوحيد للقرار),
                  Delivery\DriverAssigner (nearest/least_loaded + القبول/الرفض + السقوط لـself_claim)
jobs:             DispatchOrderToDrivers (مؤجَّل بحسب التوقيت، يعيد التحقق عند التنفيذ),
                  ExpireDriverOffer (بعد accept_timeout), (يُعاد استخدام) SendDriverAssignedNotification,
                  + SendDriverOrderCancelledNotification, SendNoDriverAvailableNotification (للمطعم)
listeners:        على OrderStatusChanged: Confirmed ⇒ جدولة الإرسال حسب التوقيت؛ Ready ⇒ إرسال فورى إن لم يُرسَل؛
                  Cancelled ⇒ إبلاغ السائق + إلغاء أى job مؤجَّل
commands:         driver:sweep-stale (كل دقيقة)
controllers:      Api\V1\Driver\{DriverAuthController, DriverAvailabilityController,
                  DriverLocationController, DriverOrderController, DriverBroadcastingController}
events:           DriverOrderOffered, DriverOrderAssigned, DriverOrderUpdated, DriverOrderAvailable, DriverForceOffline
metric:           عند dispatch_fired بلا أى سائق online فى فروع الطلب ⇒ حدث no_driver_available
panel (Filament): قسم «التوصيل بالسائقين» فى BranchForm (الإعدادان + المهل)، عمود «متصل/آخر ظهور/طلبات نشطة»
                  فى DeliveryDriversTable، خط زمنى للتوصيل فى OrderInfolist، لافتة «لا سائق متاح» فى OrdersBoard
routes:           Route::prefix('driver') ... ⚠️ route:cache إلزامى بعد النشر
lang:             lang/{ar,en}/driver.php (+ مفاتيح BranchForm فى branch.php)
tests:            tests/Feature/Driver/* (مصادقة، claim ذرّى، الأوضاع الأربعة للربط، التوقيتات الأربعة، القبول/الرفض،
                  السقوط لـself_claim، السقف، الانتقالات، idempotency، عزل الشركات، بوّابة الخطة)
```
- **🔴 لا `PlanFeature` جديدة فى المرحلة ١** (افتراضياً — انظر القرارات المعلّقة). إن قُرِّرت ميزة `DeliveryManagement` منفصلة يُطبَّق **New-Feature Checklist** كاملاً من `CLAUDE.md` الباك اند (PermissionSeeder، PlanSeeder، RoleForm، ترجمات الخطة والدور، السيدرز).
- **🔴 أى تغيير حالة يمرّ بـ`Order::transitionTo()`** — يكتب `order_status_history` ويبثّ ويشعر العميل ويعالج المخزون. لا تبثّ يدوياً بعده. **ولا تكتب `delivery_driver_id` إلا عبر `DriverAssigner`** (يوحّد التعيين اليدوى والتلقائى والالتقاط فى مكان واحد ويكتب الحدث).
- عزل المستأجرين: كل استعلام مُقيَّد بـ`company_id` من التوكن **و** بفروع السائق. اختبار «سائق شركة A لا يرى طلب شركة B» إلزامى.
- **الحسابات الزمنية بتوقيت الشركة** (`company.timezone`) — نفس مزلق `applyCompanyTimezone` فى `OrderCreationService`.

---

## 🚚 دورة حياة الرحلة داخل التطبيق

```
[غير متاح] ──(زر)──► [متاح]
                         │  حسب وضع الفرع:
                         ├─ manual/auto     → [عرض/تعيين] ──(قبول ≤45ث)──► [مُعيَّن]   (رفض ⇒ يذهب للتالى)
                         └─ self_claim      → [متاحة] ──(التقاط ذرّى)──► [مُعيَّن]
                                                                            │ «وصلت للفرع» (محلى فقط)
                                                                            ▼
                                             [استلمت الطلب] = Ready → OutForDelivery   (معطّل حتى Ready + عدّاد «جاهز خلال ~N د»)
                                                                            │ ملاحة خارجية + اتصال/واتساب بالعميل
                                                                            ▼
   ◄──────── (تلقائى بعد التسليم) ◄──── [سلّمت] = OutForDelivery → Delivered (+ نقد محصَّل)
                                                                            │
                                                                   [مشكلة] → issue (بلا تغيير حالة)
```
- **الإلغاء من المطعم** أثناء الرحلة ⇒ `DriverOrderUpdated{status=cancelled}` + إشعار + شاشة كاملة «أُلغى الطلب — أعِده للفرع».
- **لا يستطيع السائق إلغاء طلب** — يسجّل مشكلة فقط. الإلغاء باب واحد فى الباك اند (`transitionTo(Cancelled)` بسبب) من المطعم.
- **الدفع عند الاستلام:** شاشة التسليم تعرض «حصّل: X ر.س» بخط كبير، وحقل «المبلغ المحصَّل» مملوءاً بالإجمالى وقابلاً للتعديل، وتأكيد بضغطة مطوّلة.
- **الملاحة:** زر «اذهب» يفتح `google.navigation:q=lat,lng` (وWaze كخيار). **لا خريطة ملاحة داخل التطبيق.**
- **الاتصال بالعميل:** `tel:` مباشر. **الرسالة:** واتساب بقالب مترجم «أنا سائقك من {المطعم}، أقترب من موقعك».
- **عرض تلقائى ينتظر الرد:** شاشة كاملة بصوت مستقل وعدّاد 45ث وزرّى قبول/رفض ≥64dp؛ الرفض يطلب سبباً بضغطة (بعيد/مشغول/عطل).

---

## 📍 الموقع فى الخلفية — أخطر جزء فى التطبيق

- **ForegroundService** بإشعار دائم غير قابل للإزالة أثناء «متاح» («{المنصة} — أنت متاح لاستلام الطلبات»). نوع الخدمة `location`.
- الصلاحيات بالترتيب: `ACCESS_FINE_LOCATION` → ثم شاشة شرح → `ACCESS_BACKGROUND_LOCATION` («طوال الوقت») → `POST_NOTIFICATIONS`. **لا تطلب الثلاثة دفعة واحدة** (رفض تلقائى).
- التردد: متحرك 5–10ث، متوقف 30ث، متاح بلا طلب 60ث، **غير متاح = لا تتبّع إطلاقاً** (خصوصية + بطارية).
- الإرسال **مجمَّع** (batch) كل 15ث لا نبضة بنبضة؛ آخر موقع فقط يُحفظ على الخادم.
- **Heartbeat:** إن انقطع الإرسال 3 دقائق يُطفئ الخادم التوفّر ويُخبر السائق. التطبيق يعرض شارة «الاتصال منقطع» فوراً عند فشل الإرسال.
- **قتلة البطارية (Xiaomi/Oppo/Vivo/Huawei/HONOR/Samsung):** شاشة إعداد تكشف الشركة المصنّعة وتعرض **دليلاً مصوّراً** لإلغاء تحسين البطارية + رابط `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. هذا جزء من المنتج لا تفصيل.
- **قياس على الخادم:** نسبة السائقين المتاحين الذين لم يرسلوا موقعاً خلال 3 دقائق = مؤشر «قتل التطبيق». يُعرض للأدمن.
- ⚠️ **الاختبار على المحاكى لا يثبت شيئاً هنا.** التحقق النهائى على جهاز حقيقى فى الجيب لمدة ساعة (انظر بروتوكول التحقق).

---

## 🎨 معايير الواجهة (جوال السائق ≠ تابلت الكاشير)

انسخ `pos-ui-standards` وعدّل بهذه الفروق:
- **يد واحدة، وأحياناً بقفّاز، وتحت الشمس:** أزرار الرحلة الأساسية **≥ 64dp** وبعرض الشاشة، تباين عالٍ، خط كبير للعنوان والمبلغ.
- **وضع ليلى تلقائى** (المطاعم تعمل ليلاً) + وضع «سطوع عالٍ» فى النهار.
- **أهم ٣ معلومات فى أول شاشة بلا تمرير:** المطعم/الفرع، عنوان العميل، المبلغ المطلوب تحصيله.
- **الحالات الأربع** (تحميل هيكلى، فارغ مصمَّم، خطأ داخلى بإعادة محاولة، **بلا اتصال**) لكل شاشة.
- **RTL** لـar/ur و**LTR** لـen/bn/hi. الأرقام والهواتف والمبالغ **لاتينية دائماً** ومعزولة الاتجاه (`BidiText.ltr` كما POS).
- `@Preview(locale=...)` لكل لغة مدعومة على كل شاشة.

### اللغات
- الملفات: `values/` (en أساس) + `values-ar/` + `values-ur/` + `values-bn/` (+ `values-hi/`). `localeFilters` فى `app/build.gradle.kts` يطابقها.
- **الترجمة يكتبها الوكيل ثم تُراجَع بشرياً من ناطق أصلى قبل الإطلاق العام** (سطر فى قائمة ما قبل النشر). استخدم صيغ الجمع (`<plurals>`) لا التركيب اليدوى.
- اختيار اللغة **من داخل التطبيق** (مستقل عن لغة النظام) عبر `LocaleManager` — أول شاشة بعد التثبيت هى اختيار اللغة بأعلام + أسماء اللغات بحروفها.
- `Accept-Language` يُرسَل للخادم (`LocaleInterceptor`) فتأتى رسائل الخطأ بلغة السائق (الباك اند يدعم ar/en؛ الأخرى تسقط على en — والتطبيق يترجم بالكود `code` لا بالنص).

---

## ✅ بروتوكول التحقق بعد كل تعديل (على الجهاز — لا على GitHub)

> **قاعدة صاحب المشروع:** كل تعديل يُفحص على الجهاز قبل الالتزام، حتى لا تتراكم مخاطر. لا `commit` بلا هذا البروتوكول كاملاً. `scripts/verify.sh` يشغّله دفعة واحدة؛ نتيجته تُلصَق فى رسالة التسليم.

### 0) البيئة على هذا الجهاز
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"     # لا JDK فى PATH
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"              # adb ليس فى PATH
"$ADB" devices                                                      # جهاز حقيقى متصل لاسلكياً (adb-AUXH9X5C11G00308-…)
```
- ⚠️ **بلا `JAVA_HOME` يخرج Gradle فوراً بلا سطر `BUILD`** — لا تقرأ غياب الأخطاء كنجاح. ابحث عن `BUILD SUCCESSFUL` حرفياً.
- الباك اند محلياً: `php artisan serve` فى ريبو الباك اند + `"$ADB" reverse tcp:8000 tcp:8000` + `driver.debugBaseUrl=http://127.0.0.1:8000`. أطفئ الخادم و`adb reverse --remove-all` بعدها.
- حساب تجريبى: شركة `newdemo` (`newdemo@demo.com` / `password`) — أنشئ سائقاً تجريبياً `driver@demo.com` بدور `delivery_driver` (أضفه إلى `NewdemoSeeder` فى الباك اند أول جلسة).

### 1) البناء — النكهتان معاً
```bash
./gradlew :app:assembleMeniuraDebug :app:assembleTaajDebug
./gradlew :app:hiltJavaCompileMeniuraDebug          # بعد أى حقن جديد
```
### 2) الاختبارات
```bash
./gradlew testMeniuraDebugUnitTest                  # كل الوحدات
./gradlew :core:database:testMeniuraDebugUnitTest   # ترحيلات Room — تُنفَّذ فعلياً على مخطط الإصدار السابق
./gradlew :core:network:testMeniuraDebugUnitTest    # مرآة العقد
```
- **🔴 ترحيل Room يُتحقَّق بالتشغيل لا بالقراءة:** `MigrationTestHelper` على مخطط الإصدار السابق المُصدَّر (`room.schemaLocation`). ترحيل «يبدو صحيحاً» ليس دليلاً.
- **مرآة العقد:** أى تغيير فى `openapi/driver.v1.yaml` ⇒ إعادة توليد `core/network/src/test/resources/contract/driver.v1.json` (الأمر فى رأس `BackendContractMirrorTest`).
### 3) الباك اند (إن لُمس)
```bash
php artisan test --compact --filter=Driver
vendor/bin/pint app tests database routes lang config --dirty      # مرّر المسارات صراحةً (audit/ غير متتبَّع)
```
### 4) التثبيت والدخان على الجهاز الحقيقى
```bash
"$ADB" install -r app/build/outputs/apk/meniura/debug/app-meniura-debug.apk
"$ADB" logcat -c
"$ADB" shell am start -n app.qrmenu.driver.meniura/app.qrmenu.driver.MainActivity
sleep 6
"$ADB" logcat -d | grep -E "FATAL|AndroidRuntime|ANR" && echo "❌ CRASH" || echo "✅ no crash"
"$ADB" exec-out screencap -p > _screenshots/$(date +%Y%m%d-%H%M%S)-launch.png
```
- **سيناريو الدخان الإلزامى (يدوياً بـ`uiautomator`/`input tap` بموقع النص لا بإحداثيات ثابتة):** دخول → «متاح» → يظهر طلب (أنشئه محلياً عبر tinker أو صفحة الكاشير) → التقاط/قبول → استلمت → سلّمت → الطلب `delivered` فى الباك اند + `order_status_history` + `order_delivery_events` صحيحان.
- **لقطة شاشة لكل شاشة تغيّرت** فى `_screenshots/` (غير متتبَّع)، وتُرفق فى رسالة التسليم.
- ⚠️ **للتغييرات التى تمسّ الموقع/الخلفية:** اختبار ميدانى — التطبيق «متاح» والجهاز فى الجيب 30–60 دقيقة، ثم فحص أن `last_seen_at` ظل يتحدّث. لا بديل.
### 5) بعد النجاح فقط
- حدّث `CLAUDE.md` (سجلّ القرارات / المزالق / حالة المرحلة) **فى نفس التغيير**.
- `git add` للملفات المقصودة فقط (لا `git add -A` بلا مراجعة) ثم `commit` على `main`.
- أخبر صاحب المشروع بالنتيجة (أرقام الاختبارات + ما ثُبِّت على الجهاز + اللقطات) ثم `push` بعد موافقته.

---

## 🔐 الأمان والخصوصية
- التوكن مُجزّأ `sha256` فى القاعدة، ومخزَّن على الجهاز فى `EncryptedSharedPreferences`. **401 = خروج كامل** (مسح الطابور والحالة، إغلاق القناة الحيّة).
- **لا PII فى السجلات ولا فى Sentry** (هاتف العميل، العنوان). `beforeSend` يمسحها.
- الموقع يُجمع **فقط** أثناء «متاح» ويُذكر ذلك فى شاشة الصلاحية وفى سياسة الخصوصية (`PRIVACY_POLICY_URL` لكل نكهة كما POS).
- هاتف العميل يُعرض للسائق **أثناء الرحلة فقط** ويختفى بعد التسليم من الواجهة والكاش.
- مفتاح الخرائط **مقيّد** ببصمة SHA-1 لكل نكهة/بناء، مع ميزانية وحصص يومية فى Google Cloud.
- توقيع الإصدار: `DRIVER_RELEASE_STORE_FILE / _PASSWORD / _KEY_ALIAS / _KEY_PASSWORD` من `~/.gradle/gradle.properties` — لا يُلتزم أبداً. بلا مفتاح ⇒ رفض بناء release إلا بـ`driver.allowDebugSignedRelease=true` محلياً (نفس حارس POS SEC-01).

---

## 🗺️ المراحل والخطة (بترتيب الأولوية)

### المرحلة ٠ — التأسيس (يوم إلى يومان)
**الهدف:** ريبو يبنى بالنكهتين ويُثبَّت على الجهاز ويعرض شاشة دخول بهوية المنصة.
1. `git init` + `.gitignore` (`local.properties`, `*.jks`, `_screenshots/`, `build/`, `.gradle/`, `docs/audits/`).
2. نسخ `build-logic/` و`settings.gradle.kts` و`gradle/libs.versions.toml` من POS وإعادة التسمية (`pos`→`driver`, `app.qrmenu.pos`→`app.qrmenu.driver`).
3. الوحدات الفارغة حسب البنية أعلاه + `app` بنكهتين + أيقونة مؤقتة لكل نكهة.
4. نسخ `core/network` (بلا واجهات POS) + `core/datastore` (TokenStore, LocaleManager بـ5 لغات) + `core/designsystem`.
5. `.claude/skills/` منسوخة ومعدَّلة + `scripts/verify.sh` + `PLAN.md`.
6. شاشة اختيار اللغة + شاشة الدخول (تقرأ `GET /api/v1/pos/branding`).
- **معيار القبول:** `verify.sh` أخضر بالكامل، والتطبيق يُثبَّت ويفتح على الجهاز الحقيقى بالنكهتين، وتبديل اللغة يعمل للخمس لغات بأرقام لاتينية.

### المرحلة ١ — MVP «سائق المطعم» (٦–٨ أسابيع) ← **الأولوية القصوى**
**الهدف:** مطعم على منيورا يضيف سائقيه من لوحته، ويختار طريقة الربط وتوقيت الإرسال، والسائق يستلم ويوصّل ويعلّم التسليم، والمطعم يرى كل ذلك.

| الأسبوع | الباك اند | التطبيق |
|---|---|---|
| ١ | `DriverToken`/`DriverAuth`/login/logout/me + `fleets` + `driver_profiles` + اختبارات | الدخول الحقيقى + اختيار الفرع + الجلسة + الخروج القسرى |
| ٢ | availability + location (Redis) + sweep + بثّ `driver.{id}` | «متاح/غير متاح» + ForegroundService + الإرسال المجمَّع + Heartbeat + شاشة البطارية |
| ٣ | إعدادات الفرع (الوضعان + المهل) + `DispatchPolicy` + `DispatchOrderToDrivers` (التوقيتات الأربعة) + `order_delivery_events` + `no_driver_available` | قائمتا الطلبات + بطاقة الطلب + عدّاد «جاهز خلال» + الطابور المحلى (Room outbox) |
| ٤ | `DriverAssigner` (manual/nearest/least_loaded/self_claim) + `claim` ذرّى + `accept/decline` + `ExpireDriverOffer` + `release` + السقف | شاشة العرض التلقائى بعدّاد + الالتقاط الذاتى + القبول/الرفض |
| ٥ | `picked-up` + `delivered` (+نقد) + `issue` + إشعارات OneSignal + Reverb + إلغاء الطلب للسائق | شاشة الرحلة: اذهب/اتصل/واتساب/استلمت/سلّمت (+نقد)/مشكلة + حالة الإلغاء |
| ٦ | لوحة الشركة: قسم التوصيل فى BranchForm، متصل/آخر ظهور/طلبات نشطة، خط زمنى التوصيل، لافتة «لا سائق»، `DriverOrderResource` | سجلّ اليوم + الإعدادات + Sentry + التلميع (الحالات الأربع لكل شاشة) |
| ٧–٨ | تصليب: عزل المستأجرين، idempotency، أداء الاستعلامات، `route:cache`، سياسة الخصوصية | **بيتا مغلقة: 3–5 سائقين حقيقيين على جهازين رخيصين (Xiaomi/Samsung) لمدة أسبوعين** + إصلاح ما يظهر |

- **معيار القبول (end-to-end على جهاز حقيقى، لكل وضع ربط وكل توقيت):** إنشاء سائق من اللوحة → دخوله → متاح → طلب توصيل يُؤكَّد → يصل للسائق فى الوقت الذى يحدده إعداد الفرع (≤5ث من الاستحقاق) وبالطريقة التى يحددها (يدوى/أقرب/أقل حملاً/التقاط) → المطعم يراه مُعيَّناً مع سبب الاختيار → استلمت (بعد Ready فقط) → العميل يرى «فى الطريق» فى صفحة الطلب → سلّمت (+نقد) → `delivered` + `paid` + الخط الزمنى كامل → التطبيق فى الجيب 60 دقيقة ولم ينقطع.
- **ما ليس فى المرحلة ١ عمداً:** موجات متعددة وETA عبر API (المسافة هوائية فقط)، تجميع الطلبات، OTP للعميل، صورة إثبات، تقييم، خريطة حيّة للمطعم، تتبّع حىّ للعميل، أى دفتر مالى للمنصة، iOS.

### المرحلة ٢ — التوزيع المتقدّم (٤–٦ أسابيع، بعد استقرار ١)
- **ETA حقيقى** عبر Routes API لأقرب 5 فقط (بعد ترشيح Redis GEO المجانى)، وتوقيت الإرسال يخصم زمن وصول السائق الفعلى.
- **موجات العرض:** الأفضل وحده 20ث → أفضل 3 → الكل → تصعيد. نسبة القبول ضمن الترتيب.
- **الإيجار:** المُقبِل الذى لا يتحرّك نحو الفرع 5 دقائق يعود الطلب للطابور.
- **التجميع:** طلبان من نفس الفرع خلال 5د ووجهتان ≤2كم = رحلة واحدة. **لا ثالث.**
- خريطة حيّة للمطعم فى اللوحة + موقع السائق للعميل فى صفحة الطلب (endpoint عام بـ`lookup_token`، استطلاع 10ث).

### المرحلة ٣ — الجودة والمال للمطعم (٣–٤ أسابيع)
- تسوية نقد السائق لكل وردية (على مستوى المطعم — المال للمطعم لا للمنصة)، وتقرير توصيلات/نقد كل سائق للمطعم.
- إثبات التسليم: OTP من العميل (يُرسَل واتساب) + صورة اختيارية.
- تقييم السائق من العميل + مؤشرات (زمن الوصول مقابل المتوقَّع، الإلغاء بعد القبول).
- «عميل لا يرد» بمؤقّت ومحاولتين وقرار موثَّق.

### المرحلة ٤ — أسطول المنصة (٦–٩ أشهر، **قرار منفصل**)
- **البوّابة:** `no_driver_available ≥ 15٪` فى حىّ واحد + 5 مطاعم تطلبه + **رأى قانونى مكتوب** فى الترخيص (الهيئة العامة للنقل)، ووضع السائق المستقل (وثيقة العمل الحر للسعودى، والمقيم يحتاج غطاءً نظامياً)، والتأمين، وضريبة الخدمة.
- انضمام السائق العام (وثائق + اعتماد أدمن + عقد + IBAN)، دفتر أستاذ لكل سائق، **سقف نقدى COD**، تسويات، حوافز، كونسول عمليات المنصة.
- **الأجر لكل رحلة يُحسب على نقطة التعادل** (مثال: ثابت 25 ألف ر.س/شهر ÷ هامش 3.6 ر.س = ~7,000 رحلة/شهر ≈ 230/يوم). المدينة الواحدة والحىّ الكثيف أولاً.

---

## 🚀 خطة الجلسة الأولى (خطوة بخطوة)
1. اقرأ هذا الملف كاملاً + رأس `CLAUDE.md` فى ريبو الباك اند + `README.md` و`build-logic/` فى ريبو POS.
2. تحقّق من البيئة: `JAVA_HOME`، `adb devices` يرى الجهاز، `php artisan serve` يعمل.
3. نفّذ المرحلة ٠ بالترتيب. **بعد كل خطوة كبيرة: `verify.sh`.**
4. اعرض على صاحب المشروع لقطة شاشة الدخول بالنكهتين قبل الانتقال للمرحلة ١.
5. أنشئ `openapi/driver.v1.yaml` فى ريبو الباك اند **قبل** أى كنترولر (العقد أولاً)، وانسخه هنا.
6. ابدأ الأسبوع ١ من المرحلة ١.
- **حجم المهمة للوكيل:** «شاشة الرحلة: زر استلمت + الطابور + اختبار» لا «ابنِ تطبيق السائق».

---

## ❓ قرارات معلّقة لصاحب المشروع (مع التوصية)

> الافتراضات أدناه مطبَّقة فى هذا الملف حتى يُقرَّر غيرها. حين يُحسم قرار، عدّل الجدول وسجّله فى «سجلّ القرارات».

| # | القرار | الخيارات | التوصية (المطبَّقة افتراضياً) |
|---|---|---|---|
| ١ | الإشعارات | OneSignal (موجود) / FCM مباشر | **OneSignal** — صفر عمل خلفى، والانتقال لـFCM لاحقاً إن تأخّر التسليم |
| ٢ | بوّابة الخطة | إعادة استخدام `DeliveryAreas` / ميزة جديدة `DeliveryManagement` تُباع منفصلة | **`DeliveryAreas`** فى المرحلة ١؛ فصلها قرار تجارى لاحق |
| ٣ | دخول السائق | بريد+كلمة مرور (كما تنشئه اللوحة اليوم) / جوال+OTP | **بريد+كلمة مرور** الآن (لا مزود SMS)؛ OTP فى المرحلة ٣ |
| ٤ | الافتراضى لطريقة الربط للفروع القائمة | `manual` / `self_claim` / `auto_least_loaded` | **`manual`** — يطابق سلوك اليوم بلا مفاجأة؛ والمطعم يغيّره بوعى |
| ٥ | الافتراضى لتوقيت الإرسال | `on_ready` / `by_max_item_prep_time` | **`on_ready`** — الأبسط والأدق؛ التقدير بزمن التحضير يحتاج أن تكون `preparation_time` مضبوطة على الأصناف |
| ٦ | اللغات | ar/en/ur/bn / + hi | **أضف hi** (تكلفته شبه صفر بعد البنية) |
| ٧ | الخرائط | Google / Mapbox | **Google** بمفتاح المنصة + ملاحة خارجية |
| ٨ | تتبّع العميل للسائق | فى المرحلة ١ / تأجيله | **تأجيله للمرحلة ٢** — اسم وجوال السائق يظهران للعميل اليوم أصلاً |
| ٩ | جهاز الاختبار | الجهاز المتصل حالياً / هاتف متوسط إضافى | **هاتف أندرويد رخيص إضافى (Xiaomi أو Samsung A)** — الجهاز الحالى HONOR/Huawei وسلوك بطاريته لا يمثّل السوق كله |
| ١٠ | اسم الريبو على GitHub | — | ✅ **مُتّخذ:** `https://github.com/mazenelmasry/driver-android-saas-whatsapp-qrmenu.git` |
| ١١ | `CLAUDE.md` متتبَّع | نعم / لا | **نعم** (لا خادم يسحبه) |

### اقتراحات إضافية (اختيارية — قرّر لاحقاً)
- **نطق العرض/التعيين صوتياً بلغة السائق** (TTS) — السائق يقود ولا يقرأ.
- **رابط تتبّع للعميل عبر واتساب** يعيد استخدام صفحة `order/[orderNumber]` فى Next.js (المرحلة ٢).
- **ملخّص يومى للمطعم**: رحلات كل سائق، متوسط زمن التسليم، النقد المحصَّل (المرحلة ٣).
- **سائق مشترك بين فرعين** (`branch_ids[]`) — البنية تسمح؛ تفعيله بقرار.
- **وضع «ذروة»** يدوى من اللوحة يقلب التوقيت مؤقتاً إلى `on_new_order` ويرفع السقف لكل سائق.

---

## 🛑 بروتوكول التحديث الذاتى لهذا الملف
حدّث هذا الملف **فى نفس التغيير** حين:
1. تضيف وحدة `core/*` أو `feature/*` → قسم البنية.
2. تضيف/تغيّر تبعية أو إصداراً → الحزم والإصدارات.
3. تمسّ مخطط Room → «سجلّ مخطط Room» (رقم الإصدار، التاريخ، السبب، **وأنك نفّذت الترحيل فعلياً**).
4. تضيف endpoint → «عقد الـAPI» + `openapi/driver.v1.yaml` + المرآة.
5. تتخذ قراراً معمارياً غير مكتوب → «سجلّ القرارات» بتاريخ.
6. تصرف >30 دقيقة على مزلق → «المزالق».
7. تُنهى خطوة من مرحلة → حالة المرحلة.
**حين يتعارض الملف مع الكود: الكود يفوز — لكن يجب التوفيق بينهما قبل نهاية الجلسة.**

### حالة المراحل
| المرحلة | الحالة | آخر تحديث |
|---|---|---|
| ٠ التأسيس | ⏳ لم تبدأ | 2026-09-21 |
| ١ MVP سائق المطعم | ⏳ لم تبدأ | |
| ٢ التوزيع المتقدّم | ⏳ | |
| ٣ الجودة والمال | ⏳ | |
| ٤ أسطول المنصة | ⛔ بانتظار العتبة + رأى قانونى | |

### سجلّ مخطط Room
| الإصدار | التاريخ | التغيير | تحقُّق الترحيل |
|---|---|---|---|
| 1 | — | `driver_actions_outbox`, `orders_cache` | — |

### سجلّ القرارات
| التاريخ | القرار | السبب |
|---|---|---|
| 2026-09-21 | Kotlin/Compose أصلى، ريبو منفصل، أنماط منسوخة من POS | الموقع فى الخلفية جوهرى؛ نطاق الضرر؛ Flutter هُجر فعلاً |
| 2026-09-21 | المرحلة ١ = أسطول المطعم؛ الأسطول المشترك مؤجَّل لعتبة رقمية | السيولة والترخيص؛ ١ تقيس سوق ٤ |
| 2026-09-21 | السائق = `User` بدور `delivery_driver` + `driver_profiles` + `fleets` من اليوم الأول | إعادة استخدام شاشة اللوحة القائمة بلا تغيير |
| 2026-09-21 | طريقة الربط وتوقيت الإرسال **إعدادان لكل فرع** بأربع قيم لكلٍّ، عبر `DispatchPolicy` وحده | طلب صاحب المشروع؛ سطح قرار واحد يمنع الانحراف |
| 2026-09-21 | Redis GEO للموقع، MySQL للـ`last_seen_at` فقط | حجم الكتابة |
| 2026-09-21 | لا CI على GitHub؛ التحقق على الجهاز | قرار صاحب المشروع |

### المزالق والدروس (مبذورة من تجربة POS — أضف هنا)
- **بلا `JAVA_HOME` يخرج Gradle بلا سطر `BUILD`** — ابحث عن `BUILD SUCCESSFUL` حرفياً.
- **المحاكى يُفسد أجسام الردود الكبيرة من `php artisan serve`** — استخدم `adb reverse` + `127.0.0.1:8000`؛ و`php artisan serve` أحادى الخيط فاجعل `maxRequests=1` فى debug.
- **`DnsRetryInterceptor` ضرورى على HONOR/Huawei** — أول طلب بعد الاستيقاظ يفشل DNS.
- **`name_ar` يجب أن يكون nullable فى كل DTO** — شركة/فرع بلا اسم عربى كان يُسقط تحليل JSON فى POS.
- **حفظ ما بعد الدخول داخل `NonCancellable`** — الانتقال للشاشة التالية كان يلغيه.
- **الحقن الجديد يحتاج `hiltJavaCompile…`** — خطأ Dagger لا يظهر فى `assemble` وحده أحياناً.
- **ترحيل Room «الصحيح بالقراءة» ليس دليلاً** — نفّذه على مخطط الإصدار السابق.
- **الأرقام العربية فى الهاتف/المبلغ** — عزل الاتجاه + أرقام لاتينية عبر `LocaleManager`.
- **الضغط على النص فى صف إعداد يقلب مفتاحه** — عند الأتمتة انقر بموقع العنصر لا بالبحث النصى.
- **`FlowRow` لا `Row` لرقائق العربية** — `Row` تكسر النص حرفاً فى كل سطر.
- **الحسابات الزمنية بتوقيت الشركة لا الخادم** — `preparation_time` + `confirmed_at` يُقارنان فى `company.timezone`.
