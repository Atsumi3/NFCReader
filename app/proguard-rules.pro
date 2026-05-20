# Android NFC Tag technologies are resolved at runtime via Kotlin reflection
# (see NfcReadViewModel#resolveIntent and AnyExtensions#allGetterResults).
-keep class android.nfc.tech.** { *; }
-keepclassmembers class android.nfc.tech.** { *; }
