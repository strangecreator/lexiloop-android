# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class ru.lexiloop.app.**$$serializer { *; }
-keepclassmembers class ru.lexiloop.app.** {
    *** Companion;
}
-keepclasseswithmembers class ru.lexiloop.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces are used reflectively.
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
