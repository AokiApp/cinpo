#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./create.sh <destination-directory>

Fetches the CINPO template with git into a temporary sparse checkout, copies
./template into a new project directory, and prepares it as a standalone starter
project.

When run from a CINPO Git checkout, the script fetches the template from the
current origin/HEAD commit so the generated project matches that checked-out
revision. Otherwise it defaults to:
  repo: https://github.com/yuki-js/cinpo
  ref:  main

Environment overrides:
  CINPO_TEMPLATE_REPO  Git repository URL used when auto-detection is unavailable.
  CINPO_TEMPLATE_REF   Git ref used when auto-detection is unavailable.
EOF
}

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Required command not found: ${command_name}" >&2
    exit 1
  fi
}

detected_repo=""
detected_ref=""

detect_checkout_source() {
  local candidate_dir="$1"
  local repo_url=""
  local ref=""

  if [[ -z "${candidate_dir}" ]]; then
    return 1
  fi

  if ! git -C "${candidate_dir}" rev-parse --show-toplevel >/dev/null 2>&1; then
    return 1
  fi

  repo_url="$(git -C "${candidate_dir}" remote get-url origin 2>/dev/null || true)"
  ref="$(git -C "${candidate_dir}" rev-parse HEAD 2>/dev/null || true)"

  if [[ -z "${repo_url}" || -z "${ref}" ]]; then
    return 1
  fi

  detected_repo="${repo_url}"
  detected_ref="${ref}"
}

checkout_sparse_paths() {
  local repo_url="$1"
  local ref="$2"
  local checkout_dir="$3"
  shift 3

  git init -q "${checkout_dir}"
  git -C "${checkout_dir}" remote add origin "${repo_url}"
  git -C "${checkout_dir}" config advice.detachedHead false
  git -C "${checkout_dir}" config core.sparseCheckout true

  mkdir -p -- "${checkout_dir}/.git/info"
  : > "${checkout_dir}/.git/info/sparse-checkout"

  local sparse_path=""
  for sparse_path in "$@"; do
    printf '%s\n' "${sparse_path}" >> "${checkout_dir}/.git/info/sparse-checkout"
  done

  git -C "${checkout_dir}" fetch --depth 1 origin "${ref}"
  git -C "${checkout_dir}" checkout --detach -q FETCH_HEAD
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -ne 1 ]]; then
  usage >&2
  exit 1
fi

require_command git
require_command awk
require_command mktemp

script_dir=""
script_path="${BASH_SOURCE[0]:-}"
if [[ -n "${script_path}" ]]; then
  script_dir="$(cd -- "$(dirname -- "${script_path}")" >/dev/null 2>&1 && pwd || true)"
fi

default_repo="https://github.com/yuki-js/cinpo"
default_ref="main"
destination_input="$1"

if [[ -z "${CINPO_TEMPLATE_REPO:-}" || -z "${CINPO_TEMPLATE_REF:-}" ]]; then
  detect_checkout_source "${script_dir}" || true
fi

template_repo="${CINPO_TEMPLATE_REPO:-${detected_repo:-${default_repo}}}"
template_ref="${CINPO_TEMPLATE_REF:-${detected_ref:-${default_ref}}}"

if [[ "${destination_input}" = /* ]]; then
  destination_dir="${destination_input}"
else
  destination_dir="${PWD}/${destination_input}"
fi

if [[ -e "${destination_dir}" ]]; then
  echo "Destination already exists: ${destination_dir}" >&2
  exit 1
fi

work_dir="$(mktemp -d)"
cleanup() {
  rm -rf -- "${work_dir}"
}
trap cleanup EXIT

template_checkout_dir="${work_dir}/cinpo-source"
checkout_sparse_paths "${template_repo}" "${template_ref}" "${template_checkout_dir}" "template/"

template_dir="${template_checkout_dir}/template"
if [[ ! -d "${template_dir}" ]]; then
  echo "template directory not found in fetched source: ${template_repo} @ ${template_ref}" >&2
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
escaped_project_name="${escaped_project_name//\'/\\\'}"
settings_file="${destination_dir}/settings.gradle"

if [[ ! -f "${settings_file}" ]]; then
  echo "settings.gradle not found in extracted template: ${settings_file}" >&2
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

# --- Prepare Gradle Wrapper from official source ---
gradle_version="9.3.1"
gradle_repo="https://github.com/gradle/gradle"
gradle_ref="v${gradle_version}"
wrapper_dir="${destination_dir}/gradle/wrapper"
mkdir -p -- "${wrapper_dir}"

gradle_checkout_dir="${work_dir}/gradle-source"
checkout_sparse_paths \
  "${gradle_repo}" \
  "${gradle_ref}" \
  "${gradle_checkout_dir}" \
  "gradle/wrapper/gradle-wrapper.jar" \
  "gradlew" \
  "gradlew.bat"

if [[ ! -f "${gradle_checkout_dir}/gradle/wrapper/gradle-wrapper.jar" ]]; then
  echo "gradle-wrapper.jar not found in fetched Gradle source: ${gradle_repo} @ ${gradle_ref}" >&2
  exit 1
fi

cp -a -- "${gradle_checkout_dir}/gradle/wrapper/gradle-wrapper.jar" "${wrapper_dir}/gradle-wrapper.jar"
cp -a -- "${gradle_checkout_dir}/gradlew" "${destination_dir}/gradlew"
cp -a -- "${gradle_checkout_dir}/gradlew.bat" "${destination_dir}/gradlew.bat"
chmod +x "${destination_dir}/gradlew"

cat > "${wrapper_dir}/gradle-wrapper.properties" <<PROPS
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-${gradle_version}-all.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPS

cat <<EOF
Created project from template:
  ${destination_dir}

Template source:
  ${template_repo} @ ${template_ref}

Before running, you may need to prepare vendor directory:
  - Download the Java Card SDK Tools and Simulator from Oracle's website.
  - Place these archives without extracting them into
    ${destination_dir}/vendor

Next steps:
  0. prepare vendor directory
  1. cd ${destination_dir}
  2. ./gradlew cinpoRun
EOF
