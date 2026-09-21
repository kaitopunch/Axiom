# Axiom stores the app's record classes as JSON through Gson, which reads fields reflectively. Under
# full-mode R8 those fields would be stripped and every stored payload would read back empty — the
# consumer's rule to write for its own types (axiom/README.md §1, §7).
-keep class com.duylt.demo.axiom.data.record.** { *; }
-keep class com.duylt.demo.axiom.data.remote.model.** { *; }
