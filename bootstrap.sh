#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Style / colour support
#
# All decorative output (banner, progress, configuration panel) goes to STDERR.
# The machine-readable summary goes to STDOUT.
#
# Colours are emitted only when STDERR is an interactive TTY and NO_COLOR is
# not set (https://no-color.org/).  When disabled every variable expands to
# the empty string so the rest of the script needs no conditionals.
# ---------------------------------------------------------------------------

if [[ -t 2 && -z "${NO_COLOR:-}" ]]; then
  _CYAN=$'\033[0;36m'
  _GREEN=$'\033[0;32m'
  _RED=$'\033[0;31m'
  _YELLOW=$'\033[0;33m'
  _BOLD=$'\033[1m'
  _DIM=$'\033[2m'
  _RESET=$'\033[0m'
  _STYLE=1
else
  _CYAN="" _GREEN="" _RED="" _YELLOW="" _BOLD="" _DIM="" _RESET=""
  _STYLE=0
fi

# ---------------------------------------------------------------------------
# Banner
# ---------------------------------------------------------------------------

print_banner() {
  printf >&2 '%s' "${_CYAN}"
  cat >&2 <<'BANNER'
       し       ん       ぽ   
  _____ _____ __   _ _____   ____
 / ____|__ __|  \ | |  __ \ / __ \
| |      | | | | \| | |__) | |  | |
| |____ _| |_| |\ | |  ___/| |__| |
 \_____|_____|_| \__|_|     \____/
BANNER
  printf >&2 '%s' "${_RESET}"
  printf >&2 '%s        :: project bootstrapper ::%s\n' "${_DIM}" "${_RESET}"
}

# ---------------------------------------------------------------------------
# UI helpers (all output to STDERR)
# ---------------------------------------------------------------------------

print_section() {   # print_section <title>
  printf >&2 '\n%s==> %s%s\n' "${_BOLD}" "$1" "${_RESET}"
}

print_info() {      # print_info <key> <value>
  printf >&2 '    %s%-14s%s %s\n' "${_DIM}" "$1" "${_RESET}" "$2"
}

print_warn() {      # print_warn <message>
  printf >&2 '%s[!]%s %s\n' "${_YELLOW}" "${_RESET}" "$1"
}

die() {             # die <message>
  printf >&2 '%s[x] Error:%s %s\n' "${_RED}" "${_RESET}" "$1"
  exit 1
}

# ---------------------------------------------------------------------------
# Step helpers  ([..] -> [OK] / [x] in-place rewrite when on a TTY)
# ---------------------------------------------------------------------------

step_start() {      # step_start <n> <N> <label>
  local n="$1" N="$2" label="$3"
  if [[ "${_STYLE}" -eq 1 ]]; then
    printf >&2 '%s[..]%s  [%s/%s] %s...' "${_DIM}" "${_RESET}" "${n}" "${N}" "${label}"
  else
    printf >&2 '[..] [%s/%s] %s...\n' "${n}" "${N}" "${label}"
  fi
}

step_ok() {         # step_ok <n> <N> <label>
  local n="$1" N="$2" label="$3"
  if [[ "${_STYLE}" -eq 1 ]]; then
    printf >&2 '\r%s[OK]%s  [%s/%s] %s\n' "${_GREEN}" "${_RESET}" "${n}" "${N}" "${label}"
  else
    printf >&2 '[OK] [%s/%s] %s\n' "${n}" "${N}" "${label}"
  fi
}

step_fail() {       # step_fail <n> <N> <label> [logfile]
  local n="$1" N="$2" label="$3" logfile="${4:-}"
  if [[ "${_STYLE}" -eq 1 ]]; then
    printf >&2 '\r%s[x]%s  [%s/%s] %s (failed)\n' "${_RED}" "${_RESET}" "${n}" "${N}" "${label}"
  else
    printf >&2 '[x] [%s/%s] %s (failed)\n' "${n}" "${N}" "${label}"
  fi
  if [[ -n "${logfile}" && -s "${logfile}" ]]; then
    printf >&2 '\n%s--- captured output ---%s\n' "${_DIM}" "${_RESET}"
    cat >&2 -- "${logfile}"
    printf >&2 '%s------------------------%s\n' "${_DIM}" "${_RESET}"
  fi
}

# run_step <n> <N> <label> -- <command> [args...]
# Captures stdout+stderr of the command; shows [..] before and [OK]/[x] after.
# On failure the captured log is dumped to stderr before exiting.
run_step() {
  local n="$1" N="$2" label="$3"
  shift 3
  if [[ "${1:-}" == "--" ]]; then shift; fi

  local log_file
  log_file="$(mktemp)"

  step_start "${n}" "${N}" "${label}"

  local exit_code=0
  "$@" >"${log_file}" 2>&1 || exit_code=$?

  if [[ "${exit_code}" -eq 0 ]]; then
    step_ok "${n}" "${N}" "${label}"
    rm -f -- "${log_file}"
  else
    step_fail "${n}" "${N}" "${label}" "${log_file}"
    rm -f -- "${log_file}"
    exit "${exit_code}"
  fi
}

# ---------------------------------------------------------------------------
# Usage
# ---------------------------------------------------------------------------

usage() {
  printf >&2 '\n%s%sUsage:%s  ./scripts/bootstrap.sh [--template <name>] [--name <name>] <destination>\n\n' \
    "${_BOLD}" "${_CYAN}" "${_RESET}"
  printf >&2 'Fetches the CINPO template with git into a temporary sparse checkout, copies\n'
  printf >&2 './template/<template-name> into a new project directory, and prepares it as a\n'
  printf >&2 'standalone starter project.\n\n'
  printf >&2 '%sOptions:%s\n' "${_BOLD}" "${_RESET}"
  printf >&2 '  %s--template%s <name>   Template to use (default: %sbasic%s)\n' \
    "${_CYAN}" "${_RESET}" "${_BOLD}" "${_RESET}"
  printf >&2 '  %s--name%s <name>       Project name (default: destination directory basename)\n' \
    "${_CYAN}" "${_RESET}"
  printf >&2 '  %s-h, --help%s          Show this help\n\n' "${_CYAN}" "${_RESET}"
  printf >&2 '%sSource detection:%s\n' "${_BOLD}" "${_RESET}"
  printf >&2 '  When run from a CINPO Git checkout, the script fetches the template from the\n'
  printf >&2 '  current origin/HEAD commit so the generated project matches that checked-out\n'
  printf >&2 '  revision. Otherwise it defaults to:\n'
  printf >&2 '    repo: https://github.com/AokiApp/cinpo\n'
  printf >&2 '    ref:  main\n\n'
  printf >&2 '%sEnvironment overrides:%s\n' "${_BOLD}" "${_RESET}"
  printf >&2 '  %sCINPO_TEMPLATE_REPO%s  Git repository URL used when auto-detection is unavailable.\n' \
    "${_CYAN}" "${_RESET}"
  printf >&2 '  %sCINPO_TEMPLATE_REF%s   Git ref used when auto-detection is unavailable.\n\n' \
    "${_CYAN}" "${_RESET}"
}

# ---------------------------------------------------------------------------
# Prerequisite check
# ---------------------------------------------------------------------------

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    die "Required command not found: ${command_name}"
  fi
}

# ---------------------------------------------------------------------------
# Git source detection
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# Sparse checkout helper
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------

selected_template="basic"
project_name=""
destination_input=""

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -h|--help)
      print_banner
      usage
      exit 0
      ;;
    --template)
      if [[ "$#" -lt 2 || -z "${2:-}" ]]; then
        die "Missing value for --template"
      fi
      selected_template="$2"
      shift 2
      ;;
    --template=*)
      selected_template="${1#--template=}"
      if [[ -z "${selected_template}" ]]; then
        die "Missing value for --template"
      fi
      shift
      ;;
    --name)
      if [[ "$#" -lt 2 || -z "${2:-}" ]]; then
        die "Missing value for --name"
      fi
      project_name="$2"
      shift 2
      ;;
    --name=*)
      project_name="${1#--name=}"
      if [[ -z "${project_name}" ]]; then
        die "Missing value for --name"
      fi
      shift
      ;;
    --)
      shift
      break
      ;;
    -*)
      die "Unknown option: $1"
      ;;
    *)
      if [[ -n "${destination_input}" ]]; then
        die "Unexpected extra argument: $1"
      fi
      destination_input="$1"
      shift
      ;;
  esac
done

while [[ "$#" -gt 0 ]]; do
  if [[ -n "${destination_input}" ]]; then
    die "Unexpected extra argument: $1"
  fi
  destination_input="$1"
  shift
done

if [[ -z "${destination_input}" ]]; then
  print_banner
  usage
  exit 1
fi

if [[ "${selected_template}" = /* || "${selected_template}" == *"/"* || \
      "${selected_template}" == "." || "${selected_template}" == ".." ]]; then
  die "Invalid template name: ${selected_template}"
fi

# ---------------------------------------------------------------------------
# Resolve repo / ref / paths
# ---------------------------------------------------------------------------

require_command git
require_command awk
require_command mktemp

script_dir=""
script_path="${BASH_SOURCE[0]:-}"
if [[ -n "${script_path}" ]]; then
  script_dir="$(cd -- "$(dirname -- "${script_path}")" >/dev/null 2>&1 && pwd || true)"
fi

default_repo="https://github.com/AokiApp/cinpo"
default_ref="main"

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
  die "Destination already exists: ${destination_dir}"
fi

if [[ -z "${project_name}" ]]; then
  project_name="$(basename -- "${destination_dir}")"
fi

# ---------------------------------------------------------------------------
# Banner + configuration panel
# ---------------------------------------------------------------------------

print_banner

print_section "Configuration"
print_info "Template"     "${selected_template}"
print_info "Project name" "${project_name}"
print_info "Destination"  "${destination_dir}"
print_info "Source"       "${template_repo} @ ${template_ref}"

# ---------------------------------------------------------------------------
# Work directory + cleanup trap
# ---------------------------------------------------------------------------

work_dir="$(mktemp -d)"
cleanup() {
  rm -rf -- "${work_dir}"
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Phase 1 — fetch template
# ---------------------------------------------------------------------------

template_checkout_dir="${work_dir}/cinpo-source"

run_step 1 4 "Fetching template" -- \
  checkout_sparse_paths \
    "${template_repo}" "${template_ref}" "${template_checkout_dir}" \
    "template/${selected_template}/"

template_dir="${template_checkout_dir}/template/${selected_template}"
if [[ ! -d "${template_dir}" ]]; then
  printf >&2 '%s[x] Error:%s template not found: %s\n' "${_RED}" "${_RESET}" "${selected_template}"
  printf >&2 '    Template source: %s @ %s\n' "${template_repo}" "${template_ref}"
  exit 1
fi

# ---------------------------------------------------------------------------
# Phase 2 — copy files + configure project
# ---------------------------------------------------------------------------

_phase_copy_and_configure() {
  mkdir -p -- "$(dirname -- "${destination_dir}")"
  cp -a -- "${template_dir}/." "${destination_dir}"

  rm -rf -- \
    "${destination_dir}/.gradle" \
    "${destination_dir}/build" \
    "${destination_dir}/vendor"

  local escaped_project_name="${project_name//\\/\\\\}"
  escaped_project_name="${escaped_project_name//\'/\\\'}"
  local settings_file="${destination_dir}/settings.gradle"

  if [[ ! -f "${settings_file}" ]]; then
    echo "settings.gradle not found in extracted template: ${settings_file}" >&2
    return 1
  fi

  local temp_settings_file="${settings_file}.tmp"

  if ! awk -v replacement="rootProject.name = '${escaped_project_name}'" '
$0 == "rootProject.name = '\''cinpo-template'\''" && !replaced {
  print replacement
  replaced = 1
  next
}
{ print }
END { if (!replaced) { exit 1 } }
' "${settings_file}" > "${temp_settings_file}"; then
    rm -f -- "${temp_settings_file}"
    echo "Expected template project name declaration not found in ${settings_file}" >&2
    return 1
  fi

  mv -- "${temp_settings_file}" "${settings_file}"
}

run_step 2 4 "Copying files & configuring project" -- _phase_copy_and_configure

# ---------------------------------------------------------------------------
# Phase 3 — Gradle wrapper
# ---------------------------------------------------------------------------

gradle_version="9.3.1"
gradle_repo="https://github.com/gradle/gradle"
gradle_ref="v${gradle_version}"
wrapper_dir="${destination_dir}/gradle/wrapper"

_phase_gradle_wrapper() {
  mkdir -p -- "${wrapper_dir}"

  local gradle_checkout_dir="${work_dir}/gradle-source"
  checkout_sparse_paths \
    "${gradle_repo}" \
    "${gradle_ref}" \
    "${gradle_checkout_dir}" \
    "gradle/wrapper/gradle-wrapper.jar" \
    "gradlew" \
    "gradlew.bat"

  if [[ ! -f "${gradle_checkout_dir}/gradle/wrapper/gradle-wrapper.jar" ]]; then
    echo "gradle-wrapper.jar not found in fetched Gradle source: ${gradle_repo} @ ${gradle_ref}" >&2
    return 1
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
}

run_step 3 4 "Fetching Gradle wrapper (${gradle_version})" -- _phase_gradle_wrapper

# ---------------------------------------------------------------------------
# Phase 4 — git init
# ---------------------------------------------------------------------------

_phase_git_init() {
  git -C "${destination_dir}" init -q
  git -C "${destination_dir}" add -A
  git -C "${destination_dir}" \
    -c user.name="CINPO Template" \
    -c user.email="cinpo-template@example.invalid" \
    commit -q -m "Initial commit"
}

run_step 4 4 "Initializing git repository" -- _phase_git_init

# ---------------------------------------------------------------------------
# Success panel  (decorative chrome -> STDERR, summary -> STDOUT)
# ---------------------------------------------------------------------------

_box="+--------------------------------------------------+"

printf >&2 '\n%s%s%s\n' "${_GREEN}" "${_box}" "${_RESET}"
printf >&2 '%s|%s  %s[OK] Project created successfully!%s         %s|%s\n' \
  "${_GREEN}" "${_RESET}" "${_BOLD}" "${_RESET}" "${_GREEN}" "${_RESET}"
printf >&2 '%s|%s  %-48s%s|%s\n' \
  "${_GREEN}" "${_RESET}" "${destination_dir}" "${_GREEN}" "${_RESET}"
printf >&2 '%s%s%s\n\n' "${_GREEN}" "${_box}" "${_RESET}"

printf >&2 '%s[!]%s Before running, prepare the vendor directory:\n' "${_YELLOW}" "${_RESET}"
printf >&2 '    - Download the Java Card SDK Tools and Simulator from Oracle'"'"'s website.\n'
printf >&2 '    - Place the archives (unextracted) into\n'
printf >&2 '      %s/vendor\n\n' "${destination_dir}"

printf >&2 '%sNext steps%s\n' "${_BOLD}" "${_RESET}"
printf >&2 '    %s1.%s cd %s\n' "${_CYAN}" "${_RESET}" "${destination_dir}"
printf >&2 '    %s2.%s ./gradlew cinpoRun\n' "${_CYAN}" "${_RESET}"

# Machine-readable summary on STDOUT (suitable for piping / capture)
cat <<EOF
Created project from template:
  ${destination_dir}

Template:
  ${selected_template}

Project name:
  ${project_name}

Template source:
  ${template_repo} @ ${template_ref}
EOF
