# Project-specific R8 rules.
#
# Keep this file intentionally small. The release build uses Android's optimized
# default rules first; add library-specific rules here only when R8 proves they
# are required.

# AGP 9.1.1 bundles R8 9.1.31, which currently crashes in its optimizer on this
# app with IndexOutOfBoundsException. Keep shrinking/obfuscation/resource
# shrinking enabled, but skip the crashing optimization phase until the Android
# Gradle Plugin/R8 toolchain is upgraded.
-dontoptimize
