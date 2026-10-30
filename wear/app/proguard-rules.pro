# kotlinx.serialization: keep generated serializers for the API models.
-keepclassmembers @kotlinx.serialization.Serializable class com.watchhrept.wear.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.watchhrept.wear.data.**$$serializer { *; }
