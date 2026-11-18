# The app's own R8 rules, after AGP's proguard-android-optimize.txt and the libraries' consumer rules
# (docs/dev/android.md, R8 and the keep rules). Each rule is one the minified suite fails without.

# names stay as they are: the log's stack traces, its tags and the texts that name a class stay readable
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
# R8 cannot keep the source lines while it optimises, so it does not: 9.4 MB, not 5.0 MB (30.1 MB unshrunk)
-dontoptimize

# Android has no java.beans annotations; Jackson's JavaBeansAnnotations looks for them once, catches the error and
# does without, and the engine reads its files as trees, never through them
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# slf4j finds its provider through META-INF/services and makes it with its no-argument constructor
-keep class nl.enadeag.carchargeplanner.android.logging.LogcatServiceProvider {
    public <init>();
}
