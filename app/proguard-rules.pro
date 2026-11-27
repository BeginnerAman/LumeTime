# Keep ViewBinding and standard Android entry points
-keepclassmembers class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}
