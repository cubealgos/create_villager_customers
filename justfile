# The task surface: the fleet's standard recipe names.

main_checkout := parent_directory(`git rev-parse --path-format=absolute --git-common-dir`)
vault_spec := env("VC_VAULT_SPEC", main_checkout / ".." / "heimathafen" / "vault" / "projects" / "create_villager_customers" / "spec")

default:
    @just --list

# Resolve every dependency and prove the toolchain.
bootstrap:
    ./gradlew --version

# Static analysis and the project's own rules, without the tests.
lint:
    ./gradlew check -x test -x runGameTest

# Everything with a build step, including the jar.
build:
    ./gradlew build

# Unit tests, then the repository tools as commands.
test: test-java test-tools

test-java:
    ./gradlew test

test-tools:
    python3 -m unittest discover -s tools -p 'test_*.py'

# Server-side game tests on a headless dedicated server.
gametest:
    ./gradlew runGameTest

# Minecraft 26.2 with Create Fly and this mod.
client:
    ./gradlew runClient

# Refresh docs/spec/ from the vault; the vault is authoritative.
spec-sync:
    rsync -a --delete "{{vault_spec}}/" docs/spec/

# Render the Modrinth icon on the cubealgos navy badge (VC-9).
# Renders the vanilla emerald item sprite, read from the Minecraft client jar in the Gradle
# cache -- this mod adds no block or item of its own to draw instead (00-context.md).
icon:
    python3 tools/icon.py

# Regenerate docs/map.md and docs/map/ from the source.
map:
    python3 tools/map.py

map-check:
    python3 tools/map.py --check

# Repository conformance, read-only.
doctor: doctor-repo doctor-toolchain

doctor-repo:
    kontor doctor

doctor-toolchain:
    python3 tools/doctor.py

# Everything a merge must survive.
check: lint map-check test gametest

# The release build (REL-REQ-001): only from a clean checkout at a tag; writes dist/ with the jar,
# its SHA-256 and the notes to paste into the release.
release:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -n "$(git status --porcelain)" ]; then echo "release: the checkout is not clean"; exit 1; fi
    tag=$(git describe --exact-match --tags 2>/dev/null) || { echo "release: HEAD is not at a tag"; exit 1; }
    version=$(sed -n 's/^version = "\(.*\)"$/\1/p' build.gradle.kts)
    [ "$tag" = "v$version" ] || { echo "release: tag $tag is not v$version"; exit 1; }
    ./gradlew clean build
    rm -rf dist && mkdir dist
    cp "build/libs/create_villager_customers-$version.jar" dist/
    (cd dist && shasum -a 256 "create_villager_customers-$version.jar" > "create_villager_customers-$version.jar.sha256")
    python3 tools/release_notes.py "$version" > "dist/release-notes-$version.md"
    echo "release: dist/ holds the jar, its SHA-256 and the notes for $tag"
