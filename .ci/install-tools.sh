#!/bin/sh
# Installs `just` into the pipeline image when it is missing. Idempotent, so the same script runs
# on a developer machine as a no-op. Everything needing shell syntax lives here, not in the
# pipeline file, because Woodpecker interpolates its own variable syntax throughout that file.
set -eu

# eclipse-temurin:25-jdk ships none of curl (needed below to fetch just's tarball), git (the
# justfile's own first line runs `git rev-parse` to find the checkout) or python3 (needed by
# `just check` for tools/map.py and the tool tests). Woodpecker's first runs on
# create_brass_compass failed here in turn with "curl: not found" and "git: not found" before
# this step existed.
missing=""
for tool in curl git python3; do
  command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
done
if [ -n "$missing" ] && command -v apt-get >/dev/null 2>&1; then
  echo "installing$missing"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq >/dev/null 2>&1
  apt-get install -y -qq --no-install-recommends curl git python3 ca-certificates >/dev/null 2>&1
fi

if command -v just >/dev/null 2>&1; then
  echo "just $(just --version | awk '{print $2}') already present"
  exit 0
fi
JUST_VERSION="${JUST_VERSION:-1.58.0}"

# just's own install.sh is a bash script; the image runs this file with dash, and piping a bash
# script into `sh` there is a syntax error. The prebuilt tarball needs only curl and tar.
case "$(uname -m)" in
  x86_64) JUST_TARGET="x86_64-unknown-linux-musl" ;;
  aarch64|arm64) JUST_TARGET="aarch64-unknown-linux-musl" ;;
  *) echo "install-tools: unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac

echo "installing just ${JUST_VERSION} (${JUST_TARGET})"
curl --proto '=https' --tlsv1.2 -sSfL "https://github.com/casey/just/releases/download/${JUST_VERSION}/just-${JUST_VERSION}-${JUST_TARGET}.tar.gz" | tar -xz -C /usr/local/bin just
just --version
