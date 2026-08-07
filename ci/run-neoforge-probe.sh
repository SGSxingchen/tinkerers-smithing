#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
WORK_DIR="$ROOT_DIR/.ci-neoforge-server"
INSTALLER_URL="https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/forge-1.20.1-47.1.106-installer.jar"
INSTALLER_SHA256="c1ea1c3be532c4444004efd7f9cc71e1590d010ff44845b93effba61e3bd1526"
CONNECTOR_URL="https://github.com/Sinytra/Connector/releases/download/1.0.0-beta.46%2B1.20.1/Connector-1.0.0-beta.46%2B1.20.1.jar"
CONNECTOR_SHA256="38bc78533dbad71a5770b1f5fb703b0d9e35d417a2c21b7adbc3dc389d027dc6"
FFAPI_URL="https://github.com/Sinytra/ForgifiedFabricAPI/releases/download/0.92.2%2B1.11.9%2B1.20.1/fabric-api-0.92.2%2B1.11.9%2B1.20.1.jar"
FFAPI_SHA256="cc8365f3d2d5282c226f18f84bfef273c3966734507817a3d2a169fae8a67b76"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"
curl --fail --location --retry 3 --output neoforge-installer.jar "$INSTALLER_URL"
echo "$INSTALLER_SHA256  neoforge-installer.jar" | sha256sum --check --strict
java -jar neoforge-installer.jar --installServer
mkdir -p mods
curl --fail --location --retry 3 --output mods/connector.jar "$CONNECTOR_URL"
echo "$CONNECTOR_SHA256  mods/connector.jar" | sha256sum --check --strict
curl --fail --location --retry 3 --output mods/forgified-fabric-api.jar "$FFAPI_URL"
echo "$FFAPI_SHA256  mods/forgified-fabric-api.jar" | sha256sum --check --strict
cp "$ROOT_DIR"/build/libs/*.jar mods/
cp "$ROOT_DIR"/ci-probe/build/libs/*.jar mods/
rm -f mods/*-sources.jar mods/*-dev.jar
printf 'eula=true\n' > eula.txt
printf '%s\n' '-Dmixin.debug.verbose=true' '-Dmixin.debug.export=true' '-Dconnector.logging.markers=MIXINPATCH,MERGER' >> user_jvm_args.txt

set +e
timeout 180 ./run.sh nogui 2>&1 | tee console.log
server_status=${PIPESTATUS[0]}
set -e

test "$server_status" -eq 0 || { echo "NeoForge server exited with $server_status" >&2; exit "$server_status"; }
jq -e '.status == "PASS"' compat-result.json
! grep -Eqi 'FATAL|Mixin.*(failed|error)|crash-report' console.log
