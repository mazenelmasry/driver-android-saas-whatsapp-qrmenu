# --- Kotlin metadata + reflection ---
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, Exceptions, EnclosingMethod, InnerClasses

# --- kotlinx.serialization ---
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class app.qrmenu.pos.**$$serializer { *; }
-keepclassmembers class app.qrmenu.pos.** {
    *** Companion;
}
-keepclasseswithmembers class app.qrmenu.pos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- Retrofit / OkHttp ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**

# --- ESC/POS printer lib (DantSu/ESCPOS-ThermalPrinter-Android) ---
-keep class com.dantsu.escposprinter.** { *; }
-dontwarn com.dantsu.escposprinter.**

# --- Pusher / Reverb realtime (PosRealtimeManager) ---
# pusher-java-client deserializes events reflectively (Gson) and uses an
# embedded java-websocket client; keep them + silence optional-dep warnings.
-keep class com.pusher.client.** { *; }
-dontwarn com.pusher.client.**
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**
# pusher-java-client logs via slf4j-api but ships no binding (it's optional).
-dontwarn org.slf4j.**

# --- Firebase (Sprint 12.C) ---
# Crashlytics needs source line numbers preserved (already kept below).
# The auto-init ContentProvider must survive R8.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-dontwarn com.google.firebase.**
# `ListenableFuture` is provided by `play-services-tasks` at runtime — silence
# the missing-class warning that AGP's R8 emits during release shrink.
-dontwarn com.google.common.util.concurrent.ListenableFuture

# --- Sprint 12.D — keep the trace section name so the Macrobenchmark
#   `TraceSectionMetric("PaymentFlow.charge")` matches after R8 obfuscation.
#   (Constants in companions are inlined by the Kotlin compiler before R8
#   sees them, so this rule is defensive — if a future Sprint switches to
#   a `val`, the rule below preserves the field.)
-keepclassmembers class app.qrmenu.pos.feature.checkout.presentation.CheckoutViewModel$* {
    public static final java.lang.String TRACE_PAYMENT_FLOW_CHARGE;
}

# --- ZATCA on-device signing (Apache Santuario C14N + BouncyCastle) ---
# Santuario pulls woodstox/StAX transitively; we only use the DOM Canonicalizer,
# so those StAX/bnd classes are referenced but never invoked at runtime. Silence
# the R8 missing-class errors (the exact set R8 reported).
-dontwarn aQute.bnd.annotation.**
-dontwarn com.ctc.wstx.**
-dontwarn org.codehaus.stax2.**
# Android ships only a partial javax.xml.stream (StAX); woodstox + jakarta.xml.bind
# reference the rest, which our DOM-only C14N never touches.
-dontwarn javax.xml.stream.**
-dontwarn jakarta.xml.bind.**
-dontwarn jakarta.activation.**
# Keep Santuario's canonicalizer + its reflective Init registrations.
-keep class org.apache.xml.security.** { *; }
-dontwarn org.apache.xml.security.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- SQLCipher at-rest encryption (net.zetetic:android-database-sqlcipher) ---
# The AAR ships NO consumer ProGuard rules, and libsqlcipher.so does a JNI
# GetFieldID for `mNativeHandle` (plus native-method registration) on
# net.sqlcipher.database.SQLiteDatabase during loadLibs(). R8 renamed/stripped
# that field in the release build, so a minified install crashed at LAUNCH with
# `NoSuchFieldError: no "J" field "mNativeHandle"` inside
# register_android_database_SQLiteCompiledSql. `assemble*` cannot catch this —
# only running a real (minified) release build does. Keep the whole package
# verbatim (fields + native methods) so the JNI lookups resolve.
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# --- Crash reports — keep line numbers so Crashlytics stack traces are
#   readable even after R8 minification. The mapping file is uploaded
#   automatically by `firebaseCrashlyticsUploadMappingFileRelease`.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
