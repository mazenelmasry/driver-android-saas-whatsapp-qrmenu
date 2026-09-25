# قسم تطبيق السائقين — يُلحَق بسياسة الخصوصية الحالية للمنصة

> السياسة المنشورة حالياً (`Livewire\Landing\PrivacyPolicy` / `LandingController`) لا تذكر السائقين ولا الموقع الجغرافى إطلاقاً. هذا القسم جاهز للإلحاق بها (كسياسة واحدة للمنصة، أو كصفحة فرعية منفصلة يربطها `app.privacy_policy_url` — قرار المالك). عدّل `{اسم المنصة}` و`{الرابط}` حسب النكهة عند النشر.

---

## العربية

### تطبيق السائقين — كيف نتعامل مع بياناتك

إن كنت سائق توصيل تستخدم تطبيق سائقى {اسم المنصة} (Meniura Driver / Taaj Driver)، ينطبق عليك هذا القسم بالإضافة إلى سياسة الخصوصية العامة أعلاه.

**من يستطيع استخدام هذا التطبيق؟**
التطبيق **بدعوة فقط** من مطعم مشترك فى المنصة. لا يوجد تسجيل عام مفتوح.

**ما البيانات التى نجمعها؟**

| البيانات | لماذا | متى |
|---|---|---|
| رقم جوالك | تسجيل الدخول والتحقق (OTP عبر Firebase) وربطك بالمطعم | عند التسجيل، دائماً |
| اسمك | يظهر للمطعم والعميل كمن يسلّم الطلب | عند التسجيل |
| موقعك الجغرافى الدقيق | إسناد طلبات توصيل قريبة، وعرض مسار رحلة التوصيل للمطعم | **فقط أثناء تفعيلك "متاح"** — يتوقف فوراً عند إيقاف التوفّر |
| مسار رحلتك (نقاط موقع كل دقيقة تقريباً) | تسوية أى نزاع حول وقت/مكان التسليم | أثناء تنفيذ طلب توصيل فعلى |
| النقد الذى تحصّله من العملاء | تسوية حسابك مع المطعم فى دفتر/محفظة السائق | عند تسليم طلب مدفوع نقداً |
| توكن الإشعارات (FCM) واسم جهازك وإصدار التطبيق | إيصال إشعارات طلبات التوصيل إليك، ودعم فنى | دائماً بعد تسجيل الدخول |

**لماذا نتتبّع موقعك فى الخلفية؟**
حتى لو أقفلت شاشة هاتفك أو وضعته جانباً أثناء القيادة، نحتاج موقعك أثناء توفّرك لنرسل لك طلبات قريبة ولنعرض للعميل مكانك أثناء تنفيذ طلبه. **لا نتتبّعك إطلاقاً إن كنت "غير متاح".** يظهر إشعار دائم فى شريط الحالة طوال مدة المشاركة.

**من نشارك بياناتك معه؟**
- المطعم الذى تعمل معه (اسمك، موقعك أثناء الرحلة، حساب النقد).
- Google/Firebase، بصفته مزوّد خدمة تقنية، لأداء التحقق من رقم جوالك (OTP) وتوصيل إشعاراتك (FCM). لا يُستخدَم لأى غرض تسويقى.
- لا نبيع بياناتك ولا نشاركها مع أى معلن.

**كم تبقى بياناتك محفوظة؟**
- مسار رحلاتك التفصيلى (نقاط الموقع) يُحذَف تلقائياً بعد **30 يوماً** من تاريخ تسجيله.
- سجل طلبات التوصيل وحسابك مع المطعم يبقى ضمن سجلات المطعم المحاسبية طالما حسابك نشطاً أو حتى تسوية كل المبالغ.

**كيف تحذف حسابك؟**
- من داخل التطبيق: الحساب ← طلب حذف الحساب.
- أو من المتصفح، بلا تثبيت التطبيق: {الرابط} (`/driver/account-deletion`).
- **الحذف يُراجَع من فريقنا قبل التنفيذ** — إن كنت تحمل نقداً لم يُسوَّ مع مطعم، أو كانت رحلاتك جزءاً من سجلات محاسبية للمطعم لم تُقفَل بعد، سنتواصل معك لإتمام التسوية أولاً. هذا لحمايتك أنت والمطعم معاً من فقدان سجل مالى، وليس رفضاً للطلب.

**التواصل:**
{البريد} · واتساب {الرقم}

---

## English

### Driver App — How We Handle Your Data

If you're a delivery driver using the {Platform Name} driver app (Meniura Driver / Taaj Driver), this section applies to you in addition to the general privacy policy above.

**Who can use this app?**
The app is **invite-only**, from a restaurant already on the platform. There is no open public sign-up.

**What data do we collect?**

| Data | Why | When |
|---|---|---|
| Phone number | Sign-in and verification (OTP via Firebase), linking you to a restaurant | At registration, always |
| Name | Shown to the restaurant and customer as the person delivering the order | At registration |
| Precise location | Assigning nearby delivery offers, showing the delivery route to the restaurant | **Only while you're marked "available"** — stops the instant you go offline |
| Trip breadcrumbs (roughly one point per minute) | Resolving disputes about delivery time/location | While actively delivering an order |
| Cash collected from customers | Settling your ledger balance with the restaurant | When you deliver a cash-paid order |
| Push notification token, device name, app version | Delivering order-offer notifications to you, technical support | Always, after sign-in |

**Why do we track your location in the background?**
Even if your screen is locked or your phone is set aside while driving, we need your location while you're available so we can offer you nearby deliveries and show the restaurant the delivery route. **We never track you when you're "unavailable."** A persistent notification is shown in the status bar for the whole time your location is being shared.

**Who do we share your data with?**
- The restaurant you're working with (your name, your location during a trip, your cash ledger).
- Google/Firebase, as a technical service provider, to perform phone-number verification (OTP) and deliver notifications (FCM). Never used for advertising or marketing.
- We do not sell your data and do not share it with any advertiser.

**How long is your data kept?**
- Detailed trip location points are automatically deleted **30 days** after being recorded.
- Your delivery/order history and ledger balance remain part of the restaurant's accounting records while your account is active or until all amounts are settled.

**How do you delete your account?**
- In-app: Account → Request account deletion.
- Or from a browser, without installing the app: {URL} (`/driver/account-deletion`).
- **Deletion requests are reviewed by our team before being carried out** — if you're holding cash not yet settled with a restaurant, or your trips are part of the restaurant's unclosed accounting records, we'll contact you to complete settlement first. This protects both you and the restaurant from losing a financial record — it is not a refusal of your request.

**Contact:**
{email} · WhatsApp {number}
