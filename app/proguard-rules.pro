# Proguard rules for KKM Android
-keepclassmembers class **$WhenMappings { <fields>; }
-keep class kotlin.Metadata { *; }
-keep class dagger.hilt.** { *; }
-keep class net.sqlcipher.** { *; }
-keep class com.google.gson.** { *; }
-keep class kz.kkm.domain.model.** { *; }
-keep class kz.kkm.data.local.entity.** { *; }
