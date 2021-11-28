#!/usr/bin/env bash
set -e -x
GRAAL_VERSION="$(grep compileOnly\(\"org.graalvm.nativeimage:svm build.gradle.kts | sed -E -e 's/.*compileOnly\("org.graalvm.nativeimage:svm:(.*)"\)/\1/')"
GRAAL_HOME="/Library/Java/JavaVirtualMachines/graalvm-ce-java11-${GRAAL_VERSION}/Contents/Home"
bash -c "cd ~/Downloads && curl -z graalvm-ce-java11-darwin-amd64-${GRAAL_VERSION}.tar.gz -O -L \"https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-${GRAAL_VERSION}/graalvm-ce-java11-darwin-amd64-${GRAAL_VERSION}.tar.gz\""
sudo tar -xzf ~/Downloads/graalvm-ce-java11-darwin-amd64-"${GRAAL_VERSION}".tar.gz --directory /Library/Java/JavaVirtualMachines/
sudo xattr -r -d com.apple.quarantine "${GRAAL_HOME}"
sudo "${GRAAL_HOME}/bin/gu" install native-image