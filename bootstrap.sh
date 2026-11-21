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
  printf >&2 '\n%s%sUsage:%s  ./scripts/bootstrap.sh [--template <name>] [--version <version>] [--name <name>] <destination>\n\n' \
    "${_BOLD}" "${_CYAN}" "${_RESET}"
  printf >&2 'Creates a standalone starter project from a released template archive.\n\n'
  printf >&2 '%sOptions:%s\n' "${_BOLD}" "${_RESET}"
  printf >&2 '  %s--template%s <name>      Template to use (default: %sbasic%s)\n' \
    "${_CYAN}" "${_RESET}" "${_BOLD}" "${_RESET}"
  printf >&2 '  %s--version%s <x.y.z>      Release version to download\n' \
    "${_CYAN}" "${_RESET}"
  printf >&2 '  %s--name%s <name>          Project name (default: destination directory basename)\n' \
    "${_CYAN}" "${_RESET}"
  printf >&2 '  %s-h, --help%s             Show this help\n\n' "${_CYAN}" "${_RESET}"
  printf >&2 '%sVersion:%s\n' "${_BOLD}" "${_RESET}"
  printf >&2 '  A release-pinned copy uses the version written into default_version.\n'
  printf >&2 '  Pass --version to select a different release.\n\n'
  printf >&2 '%sEnvironment:%s\n' "${_BOLD}" "${_RESET}"
  printf >&2 '  %sCINPO_TEMPLATE_VERSION%s     Release version to download.\n' \
    "${_CYAN}" "${_RESET}"
  printf >&2 '  %sCINPO_TEMPLATE_BASE_URL%s    Base URL for template archives (default: https://AokiApp.github.io/cinpo).\n\n' \
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
# Parse arguments
# ---------------------------------------------------------------------------

template_arg=""
version_arg=""
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
      template_arg="$2"
      shift 2
      ;;
    --template=*)
      template_arg="${1#--template=}"
      if [[ -z "${template_arg}" ]]; then
        die "Missing value for --template"
      fi
      shift
      ;;
    --version)
      if [[ "$#" -lt 2 || -z "${2:-}" ]]; then
        die "Missing value for --version"
      fi
      version_arg="$2"
      shift 2
      ;;
    --version=*)
      version_arg="${1#--version=}"
      if [[ -z "${version_arg}" ]]; then
        die "Missing value for --version"
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

selected_template="${template_arg:-basic}"

if [[ "${selected_template}" = /* || "${selected_template}" == *"/"* || \
      "${selected_template}" == "." || "${selected_template}" == ".." ]]; then
  die "Invalid template name: ${selected_template}"
fi

# ---------------------------------------------------------------------------
# Resolve the template version
# ---------------------------------------------------------------------------

require_command git
require_command curl
require_command tar
require_command awk
require_command mktemp

default_version="0.5.2"
template_base_url="${CINPO_TEMPLATE_BASE_URL:-https://AokiApp.github.io/cinpo}"
while [[ "${template_base_url}" == */ ]]; do
  template_base_url="${template_base_url%/}"
done

version_pattern='^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z][0-9A-Za-z.-]*)?$'

if [[ -n "${version_arg}" ]]; then
  template_version="${version_arg}"
elif [[ -n "${CINPO_TEMPLATE_VERSION:-}" ]]; then
  template_version="${CINPO_TEMPLATE_VERSION}"
elif [[ -n "${default_version}" ]]; then
  template_version="${default_version}"
else
  die "No template version: pass --version <x.y.z>"
fi

if [[ ! "${template_version}" =~ ${version_pattern} ]]; then
  die "Invalid version: ${template_version} (expected x.y.z)"
fi

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

template_url="${template_base_url}/templates/${template_version}/${selected_template}/template.tar.gz"
template_archive=""

# ---------------------------------------------------------------------------
# Banner + configuration panel
# ---------------------------------------------------------------------------

print_banner

print_section "Configuration"
print_info "Template"     "${selected_template}"
print_info "Project name" "${project_name}"
print_info "Destination"  "${destination_dir}"
print_info "Source"       "${template_url}"

# ---------------------------------------------------------------------------
# Work directory + cleanup trap
# ---------------------------------------------------------------------------

work_dir="$(mktemp -d)"
destination_created=0
completed=0

cleanup() {
  if [[ "${completed}" -ne 1 && "${destination_created}" -eq 1 ]]; then
    rm -rf -- "${destination_dir}"
  fi
  rm -rf -- "${work_dir}"
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Phase 1 — acquire the template
# ---------------------------------------------------------------------------

_phase_fetch_template() {
  local http_code=""
  if ! http_code="$(curl -sS -L --retry 3 -o "${template_archive}" -w '%{http_code}' "${template_url}")"; then
    echo "failed to download ${template_url}" >&2
    return 1
  fi

  case "${http_code}" in
    200)
      ;;
    404)
      echo "template '${selected_template}' is not published for version ${template_version}" >&2
      echo "    ${template_url}" >&2
      return 1
      ;;
    *)
      echo "failed to download ${template_url} (HTTP ${http_code})" >&2
      return 1
      ;;
  esac
}

template_archive="${work_dir}/template.tar.gz"
run_step 1 3 "Fetching template (${selected_template} ${template_version})" -- _phase_fetch_template

# ---------------------------------------------------------------------------
# Phase 2 — extract + configure project
# ---------------------------------------------------------------------------

_phase_extract_and_configure() {
  destination_created=1

  mkdir -p -- "$(dirname -- "${destination_dir}")"
  mkdir -p -- "${destination_dir}"
  if ! tar -xzf "${template_archive}" -C "${destination_dir}"; then
    echo "failed to extract ${template_archive}" >&2
    return 1
  fi

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

run_step 2 3 "Extracting files & configuring project" -- _phase_extract_and_configure

# ---------------------------------------------------------------------------
# Phase 3 — git init
# ---------------------------------------------------------------------------

_phase_git_init() {
  git -C "${destination_dir}" init -q
  git -C "${destination_dir}" add -A
  git -C "${destination_dir}" update-index --chmod=+x gradlew
  git -C "${destination_dir}" \
    -c user.name="CINPO Template" \
    -c user.email="cinpo-template@example.invalid" \
    commit -q -m "Initial commit"
}

run_step 3 3 "Initializing git repository" -- _phase_git_init

completed=1

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

source_line="  ${template_url} @ ${template_version}"

cat <<EOF
Created project from template:
  ${destination_dir}

Template:
  ${selected_template}

Project name:
  ${project_name}

Template source:
${source_line}
EOF
