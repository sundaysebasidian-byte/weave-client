# The C bridge resolves NativeBridge methods by their stable JNI symbol names. Keep that narrow
# boundary and the two callback contracts; engine helpers and data classes can still be removed or
# optimized by R8 when no longer reachable from the Android UI/service.
-keep class io.weave.client.core.bridge.NativeBridge { *; }
-keep interface io.weave.client.core.bridge.NativeCompletion { *; }
-keep interface io.weave.client.core.bridge.NativeTunCallback { *; }

# SnakeYAML also ships an optional desktop JavaBeans accessor. ClashYamlCodec uses
# SafeConstructor plus FIELD access and handles only checked maps/lists/scalars, never beans.
# Android omits these five desktop types; do not suppress any other missing-class warnings.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# TypeDescription (and other library diagnostics) calls Class.getPackage().getName().
# Android returns null for classes R8 moves into the unnamed package, breaking YAML startup.
# Preserve only library package names; classes/members may still be shrunk and obfuscated.
-keeppackagenames org.yaml.snakeyaml,org.yaml.snakeyaml.**
