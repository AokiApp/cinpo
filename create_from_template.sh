#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./create_from_template.sh <destination-directory>

Copies the starter project from ./template into a new directory.
The copied project keeps the sample identifiers unchanged except for
rootProject.name in settings.gradle, which is set from the destination
folder name.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -ne 1 ]]; then
  usage >&2
  exit 1
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
template_dir="${script_dir}/template"
destination_input="$1"

if [[ ! -d "${template_dir}" ]]; then
  echo "Template directory not found: ${template_dir}" >&2
  exit 1
fi

if [[ "${destination_input}" = /* ]]; then
  destination_dir="${destination_input}"
else
  destination_dir="${PWD}/${destination_input}"
fi

if [[ -e "${destination_dir}" ]]; then
  echo "Destination already exists: ${destination_dir}" >&2
  exit 1
fi

mkdir -p -- "$(dirname -- "${destination_dir}")"
cp -a -- "${template_dir}" "${destination_dir}"

rm -rf -- \
  "${destination_dir}/.gradle" \
  "${destination_dir}/build" \
  "${destination_dir}/vendor"

project_name="$(basename -- "${destination_dir}")"
escaped_project_name="${project_name//\\/\\\\}"
escaped_project_name="${escaped_project_name//\'/\\'}"
settings_file="${destination_dir}/settings.gradle"

if [[ ! -f "${settings_file}" ]]; then
  echo "settings.gradle not found in copied template: ${settings_file}" >&2
  exit 1
fi

temp_settings_file="${settings_file}.tmp"

if ! awk -v replacement="rootProject.name = '${escaped_project_name}'" '
$0 == "rootProject.name = '\''cinpo-template'\''" && !replaced {
  print replacement
  replaced = 1
  next
}

{
  print
}

END {
  if (!replaced) {
    exit 1
  }
}
' "${settings_file}" > "${temp_settings_file}"; then
  rm -f -- "${temp_settings_file}"
  echo "Expected template project name declaration not found in ${settings_file}" >&2
  exit 1
fi

mv -- "${temp_settings_file}" "${settings_file}"

# --- Download Gradle Wrapper from official release ---
gradle_version="9.3.1"
wrapper_dir="${destination_dir}/gradle/wrapper"
mkdir -p "${wrapper_dir}"

cat > "${wrapper_dir}/gradle-wrapper.properties" <<PROPS
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradle_version}-all.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPS

# Download all wrapper files from the official Gradle GitHub release
gradle_raw_base="https://raw.githubusercontent.com/gradle/gradle/v${gradle_version}"

download() {
  local url="$1" dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "${dest}" "${url}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "${dest}" "${url}"
  else
    echo "Neither curl nor wget found. Cannot download: ${url}" >&2
    exit 1
  fi
}

download "${gradle_raw_base}/gradle/wrapper/gradle-wrapper.jar" "${wrapper_dir}/gradle-wrapper.jar"
download "${gradle_raw_base}/gradlew"     "${destination_dir}/gradlew"
download "${gradle_raw_base}/gradlew.bat" "${destination_dir}/gradlew.bat"
chmod +x "${destination_dir}/gradlew"

cat <<EOF
Created project from template:
  ${destination_dir}

Configured automatically:
  - settings.gradle rootProject.name = '${project_name}'
  - Gradle Wrapper ${gradle_version} (gradlew, gradlew.bat, gradle/wrapper/)

Next steps:
  1. Edit manifest.yaml and build.gradle.
  2. Rename the sample Java package and classes under applet/ and host/.
  3. Adjust profile/ and host-side task code as needed.
EOF
