# libhztags.so finds TagLibBridge's native methods by class and method name, so R8 keeps both.
-keep class com.jpd.hz.tags.TagLibBridge {
    native <methods>;
}
