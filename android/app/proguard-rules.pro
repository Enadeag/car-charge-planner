# The app's own R8 rules, after AGP's proguard-android-optimize.txt and the libraries' consumer rules
# (docs/dev/android.md, R8 and the keep rules). Each rule says what needs it: R8 itself (the build fails without it),
# checkReleaseShrunk, or the minified suite; a rule that nothing fails without does not belong here.

# names stay as they are: the log's stack traces, its tags and the texts that name a class stay readable
# (checkReleaseShrunk and the minified suite)
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
# R8 cannot keep the source lines while it optimises, so it does not; the sizes are in docs/design/android/build.md, R8
# (checkReleaseShrunk and the minified suite)
-dontoptimize

# Android has no java.beans annotations; Jackson's JavaBeansAnnotations looks for them once, catches the error and
# does without, and the engine reads its files as trees, never through them (R8 fails the build without these)
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
