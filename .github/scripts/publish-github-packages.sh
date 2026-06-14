#!/usr/bin/env bash
set -euo pipefail

readonly GITHUB_PACKAGES_REPOSITORY_URL="${GITHUB_PACKAGES_REPOSITORY_URL:-https://maven.pkg.github.com/yuki-js/cinpo}"
readonly PROJECT_VERSION="${PROJECT_VERSION:-$(./gradlew -q properties | sed -n 's/^version: //p' | tail -n 1)}"

if [[ -z "${GITHUB_ACTOR:-}" ]]; then
    echo "GITHUB_ACTOR must be set." >&2
    exit 1
fi

if [[ -z "${GITHUB_TOKEN:-}" ]]; then
    echo "GITHUB_TOKEN must be set." >&2
    exit 1
fi

if [[ -z "${PROJECT_VERSION}" ]]; then
    echo "PROJECT_VERSION could not be determined." >&2
    exit 1
fi

remote_artifact_status() {
    local artifact_path="$1"
    curl \
        --silent \
        --show-error \
        --location \
        --output /dev/null \
        --write-out '%{http_code}' \
        --user "${GITHUB_ACTOR}:${GITHUB_TOKEN}" \
        "${GITHUB_PACKAGES_REPOSITORY_URL%/}/${artifact_path}"
}

remote_artifacts_exist() {
    local artifact_path
    local http_status

    for artifact_path in "$@"; do
        http_status="$(remote_artifact_status "${artifact_path}")"
        if [[ ! "${http_status}" =~ ^2[0-9][0-9]$ ]]; then
            return 1
        fi
    done

    return 0
}

duplicate_publish_conflict_detected() {
    local log_path="$1"
    grep -Eiq 'status code (409|429)|(409|429)[[:space:]:-]*conflict|conflict[[:space:]:-]*(409|429)' "${log_path}"
}

run_publish_task() {
    local task_name="$1"
    shift
    local expected_artifacts=("$@")
    local log_path
    local gradle_status

    if remote_artifacts_exist "${expected_artifacts[@]}"; then
        echo "Skipping ${task_name}; version ${PROJECT_VERSION} is already present in GitHub Packages."
        return 0
    fi

    log_path="$(mktemp /tmp/publish-github-packages.XXXXXX.log)"
    set +e
    ./gradlew --no-daemon "${task_name}" 2>&1 | tee "${log_path}"
    gradle_status=${PIPESTATUS[0]}
    set -e

    if [[ ${gradle_status} -eq 0 ]]; then
        return 0
    fi

    if duplicate_publish_conflict_detected "${log_path}" && remote_artifacts_exist "${expected_artifacts[@]}"; then
        echo "Treating duplicate-version publish conflict as success for ${task_name}."
        return 0
    fi

    echo "Gradle publish task failed: ${task_name}" >&2
    return "${gradle_status}"
}

run_publish_task \
    publishApp.aoki.cinpo.gradlePluginMarkerMavenPublicationToGithubPackagesRepository \
    "app/aoki/cinpo/gradle/app.aoki.cinpo.gradle.gradle.plugin/maven-metadata.xml" \
    "app/aoki/cinpo/gradle/app.aoki.cinpo.gradle.gradle.plugin/${PROJECT_VERSION}/app.aoki.cinpo.gradle.gradle.plugin-${PROJECT_VERSION}.pom"

run_publish_task \
    publishApp.aoki.cinpo.prepare-java-card-toolsPluginMarkerMavenPublicationToGithubPackagesRepository \
    "app/aoki/cinpo/prepare-java-card-tools/app.aoki.cinpo.prepare-java-card-tools.gradle.plugin/maven-metadata.xml" \
    "app/aoki/cinpo/prepare-java-card-tools/app.aoki.cinpo.prepare-java-card-tools.gradle.plugin/${PROJECT_VERSION}/app.aoki.cinpo.prepare-java-card-tools.gradle.plugin-${PROJECT_VERSION}.pom"

run_publish_task \
    publishMavenJavaPublicationToGithubPackagesRepository \
    "app/aoki/cinpo/cinpo/maven-metadata.xml" \
    "app/aoki/cinpo/cinpo/${PROJECT_VERSION}/cinpo-${PROJECT_VERSION}.pom" \
    "app/aoki/cinpo/cinpo/${PROJECT_VERSION}/cinpo-${PROJECT_VERSION}.jar"

run_publish_task \
    publishPluginMavenPublicationToGithubPackagesRepository \
    "app/aoki/cinpo/cinpo-gradle-plugin/maven-metadata.xml" \
    "app/aoki/cinpo/cinpo-gradle-plugin/${PROJECT_VERSION}/cinpo-gradle-plugin-${PROJECT_VERSION}.pom" \
    "app/aoki/cinpo/cinpo-gradle-plugin/${PROJECT_VERSION}/cinpo-gradle-plugin-${PROJECT_VERSION}.jar" \
    "app/aoki/cinpo/cinpo-gradle-plugin/${PROJECT_VERSION}/cinpo-gradle-plugin-${PROJECT_VERSION}.module"

echo "GitHub Packages publish workflow completed."
