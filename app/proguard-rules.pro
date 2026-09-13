# Add project specific ProGuard rules here.
-keep class com.wishexport.app.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
