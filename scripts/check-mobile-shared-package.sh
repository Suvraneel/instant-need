#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
shared="$root/instant-need-shared"
mobile="$root/instant-need-mobile"
archive="$mobile/instant-need-shared-1.0.1.tgz"

test -f "$archive"
while IFS= read -r file; do
  relative="${file#"$shared/"}"
  tar -xOf "$archive" "package/$relative" | cmp - "$file"
done < <(find "$shared/src" -type f | sort)
tar -xOf "$archive" package/package.json | cmp - "$shared/package.json"
echo "Mobile shared package matches instant-need-shared source."
