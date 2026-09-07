# Personal debug tool — keep stack traces readable, don't rename symbols.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*

# WorkManager instantiates workers reflectively.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Tink (via androidx.security-crypto) references optional deps that aren't on the classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**
-keep class com.google.crypto.tink.** { *; }

# javax.mail / com.sun.mail (Gmail IMAP) — heavy reflection over providers and parsers.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class mailcap.** { *; }
-keep class myjava.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
