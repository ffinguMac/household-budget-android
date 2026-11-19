# Add project specific ProGuard rules here.

# DataStore Preferences의 protobuf-lite는 필드를 이름으로 찾는다.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { <fields>; }
