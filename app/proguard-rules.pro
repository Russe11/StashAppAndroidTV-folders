# Project-specific R8 rules.
#
# NOTE: the release build currently sets isMinifyEnabled=false and
# isShrinkResources=false (see app/build.gradle.kts), so R8 does NOT run and
# nothing in this file takes effect today. It exists as the staging ground for
# the keep rules that must be written *before* shrinking is ever re-enabled —
# see the "Re-enable release shrinking" entry in docs/agent-memory/optimizations.md.

# Background: AGP 9.1.1 bundles R8 9.1.31, whose optimizer crashed on this app
# (IndexOutOfBoundsException), which is why shrinking was disabled as a startup
# hotfix. `-dontoptimize` was the partial middle ground that avoided the optimizer
# crash and is kept here for whenever minify/shrink are turned back on.
-dontoptimize
