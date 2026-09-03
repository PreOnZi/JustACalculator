#!/bin/sh
#
# Xcode Cloud — runs after the repository is cloned, before the build.
#
# The iOS app's "Build shared framework" phase runs Gradle, and Gradle needs a
# JDK. Xcode Cloud's images ship none, so that phase died with nothing more
# than "Command PhaseScriptExecution failed with a nonzero exit code" — the
# shell could not start java at all. On a developer Mac the phase falls back to
# Android Studio's bundled JBR, which is why this only ever broke on CI.
#
# 21 to match that JBR (21.0.8), so CI and a developer machine run the same
# major version rather than differing quietly.

set -e

if ! command -v brew > /dev/null 2>&1; then
  echo "error: Homebrew is not available; cannot install a JDK for Gradle." >&2
  exit 1
fi

echo "Installing openjdk@21 for the Gradle build..."
brew install --quiet openjdk@21

# Put it where /usr/libexec/java_home looks, so the build phase resolves it the
# same way it would locally. Not fatal if the CI user cannot write there — the
# build phase also checks Homebrew's own prefix directly.
JDK="$(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk"
if [ -d "$JDK" ]; then
  sudo mkdir -p /Library/Java/JavaVirtualMachines 2>/dev/null || true
  sudo ln -sfn "$JDK" /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || true
fi

echo "JDK ready: $(brew --prefix)/opt/openjdk@21"
