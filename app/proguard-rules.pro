# xposed_init references this class by its fully-qualified name as a string,
# and LSPosed loads it via reflection — it must keep its original name and
# all members, or the module silently fails to load.
-keep class dev.nullcode.powerflash.MainHook { *; }

# Xposed API classes are compileOnly (provided by the framework at runtime,
# never packaged into this APK) — R8 has nothing to shrink here and doesn't
# need to resolve them fully.
-dontwarn de.robv.android.xposed.**
