# Consumers store their own record classes as JSON through Gson. Gson reads fields reflectively, so a
# consumer's R8 config must keep those classes' fields — that is the consumer's rule to write for its own
# types. Axiom's own persisted types are kept here so the SDK works under full-mode R8 out of the box.
-keep class com.axiom.envelope.ApiEnvelope { *; }
-keepclassmembers class com.axiom.envelope.ApiEnvelope { <fields>; }
-keepattributes Signature
-keepattributes *Annotation*
