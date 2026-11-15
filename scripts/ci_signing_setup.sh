#!/bin/bash

# Decodes only the signing files a workflow asks for: free (GitHub APK key), play (Play upload key)
# and google_services (Play google-services.json).
set -euo pipefail

decode_env_to_file() {
  local env_var="${1}"
  local dest_file="${2}"

  if [[ -z "${!env_var:-}" ]]; then
    echo "Error: Environment variable ${env_var} is not set or empty." >&2
    exit 1
  fi

  mkdir -p "$(dirname "${dest_file}")"
  echo "${!env_var}" | base64 --decode >"${dest_file}"
  echo "Success: Written to ${dest_file}"
}

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 <free|play|google_services>..." >&2
  exit 2
fi

for target in "$@"; do
  case "${target}" in
  free) decode_env_to_file "GITHUB_SIGNING_KEYSTORE" "signing/free/signing.keystore" ;;
  play) decode_env_to_file "PLAY_SIGNING_KEYSTORE" "signing/play/signing.keystore" ;;
  google_services) decode_env_to_file "GOOGLE_SERVICES" "app/src/play/google-services.json" ;;
  *)
    echo "Error: Unknown signing target ${target}." >&2
    exit 2
    ;;
  esac
done
