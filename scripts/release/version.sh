#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
F=gradle.properties
case "${1:-}" in
  get) sed -n 's/^marketapSdkVersion=//p' "$F" ;;
  set) sed -i.bak "s/^marketapSdkVersion=.*/marketapSdkVersion=$2/" "$F" && rm "$F.bak" ;;
  files) echo "$F" ;;
  check) [ "$(grep -c '^marketapSdkVersion=' "$F")" = 1 ] ;;
  *) exit 2 ;;
esac
