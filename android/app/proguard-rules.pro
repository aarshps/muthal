# Firebase Firestore maps documents onto model classes by field name via reflection,
# so the model classes and their no-arg constructors must survive R8 shrinking.
-keepclassmembers class com.hora.muthal.model.** {
  <init>();
  <fields>;
}
-keep class com.hora.muthal.model.** { *; }

# Keep Firestore/Firebase and Credential Manager types they need.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
