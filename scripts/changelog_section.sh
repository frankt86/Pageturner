#!/bin/bash

# Prints the notes of one version from CHANGELOG.md, for headings like "## [26.11.1] - 2026-11-20".
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
  echo "Usage: $0 <version> [changelog file]" >&2
  exit 2
fi

version="${1}"
changelog="${2:-CHANGELOG.md}"

notes=$(awk -v version="${version}" '
  /^## / {
    heading = $0
    sub(/^## +\[?/, "", heading)
    sub(/[] ].*$/, "", heading)
    if (found) exit
    found = (heading == version)
    next
  }
  # link references at the end of the file, like "[26.11.1]: https://..."
  /^\[[^]]+\]: / { next }
  found { print }
' "${changelog}" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')

if [[ -z "${notes//[[:space:]]/}" ]]; then
  echo "Error: ${changelog} has no notes for ${version}. Add a section with the heading \"## [${version}] - $(date -u +%Y-%m-%d)\"." >&2
  exit 1
fi

printf '%s\n' "${notes}"
