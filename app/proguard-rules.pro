# GeckoView ships consumer rules; these are belt-and-braces for JNI/reflection.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.mozilla.**
# Room generated code is handled by Room's consumer rules.
