#!/usr/bin/env bash
set -euo pipefail

archive="${1:-vendor.zip}"
destination="${2:-vendor}"

if [[ -z "${VENDOR_PASSWORD:-}" ]]; then
  echo "VENDOR_PASSWORD is required to extract ${archive}." >&2
  exit 1
fi

if [[ ! -f "${archive}" ]]; then
  echo "Vendor archive not found: ${archive}" >&2
  exit 1
fi

rm -rf "${destination}"
mkdir -p "${destination}"

unzip -q -P "${VENDOR_PASSWORD}" "${archive}" -d "${destination}"

required_files=(
  "java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip"
)

# Oracle ships one simulator kit per host OS. At least one must be present; which
# one is required depends on where the build eventually runs.
simulator_files=(
  "java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz"
  "java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip"
)

for required_file in "${required_files[@]}"; do
  if [[ ! -f "${destination}/${required_file}" ]]; then
    echo "Required vendor file was not extracted: ${destination}/${required_file}" >&2
    exit 1
  fi
done

present_simulators=()
for simulator_file in "${simulator_files[@]}"; do
  if [[ -f "${destination}/${simulator_file}" ]]; then
    present_simulators+=("${simulator_file}")
  fi
done

if [[ ${#present_simulators[@]} -eq 0 ]]; then
  echo "No Java Card simulator kit was extracted. Expected one of: ${simulator_files[*]}" >&2
  exit 1
fi

(
  cd "${destination}"
  all_checksums=$(cat <<'CHECKSUMS'
86443cb1b64c006456e524d91082ba25d5ebb0ee5506c6e4d7088350ce251d9d  java_card_devkit_tools-bin-v26.0-b_705-04-MAY-2026.zip
b8b999c3e1cfac5d56f7ef16654ce28c99cf472d96761d419a03ddf11f5811c0  java_card_devkit_simulator-linux-bin-v26.0-b_788-05-MAY-2026.tar.gz
5ab2efc69486989bcd73d0ed235f4f8348e82df262448fbc781dabd1bcb9b9c5  java_card_devkit_simulator-win-bin-v26.0-b_788-05-MAY-2026.zip
CHECKSUMS
)
  # Verify only what was actually extracted, so a kit for another OS may be absent.
  while IFS= read -r checksum_line; do
    [[ -f "${checksum_line##*  }" ]] || continue
    printf '%s\n' "${checksum_line}" | sha256sum -c
  done <<< "${all_checksums}"
)
