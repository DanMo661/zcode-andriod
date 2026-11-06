# kotlinx-serialization（官方推荐规则）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# 保留 @Serializable 类生成的 serializer
-keep,includedescriptorclasses class com.zcodeandriod.app.**$$serializer { *; }
-keepclassmembers class com.zcodeandriod.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.zcodeandriod.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
