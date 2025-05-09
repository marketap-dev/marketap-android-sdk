# Moshi core
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# KotlinJsonAdapterFactory 등 리플렉션 관련
-keep class kotlin.Metadata
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# JSON 어댑터들 (Codegen을 쓰는 경우)
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Moshi Adapter (Codegen이 생성한 어댑터)
-keep class **_JsonAdapter { *; }

-keep enum com.marketap.sdk.model.internal.** {
    *;
}