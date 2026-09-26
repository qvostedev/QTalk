# Linphone вызывает эти классы через JNI по строковым именам.
-keep class org.linphone.** { *; }
-keepclassmembers class org.linphone.** { *; }
-keepnames class org.linphone.**
