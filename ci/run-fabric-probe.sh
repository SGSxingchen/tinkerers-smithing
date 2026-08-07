#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
WORK_DIR="$ROOT_DIR/.ci-fabric-server"
INSTALLER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-installer/0.11.2/fabric-installer-0.11.2.jar"
INSTALLER_SHA256="c6ad5bef1bb12b5a7227be3ee540c2c906afdf4ac8114b78e0aca0b146005c27"
FAPI_URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.83.0+1.20.1/fabric-api-0.83.0+1.20.1.jar"
FAPI_SHA256="25a42fbc7b1eb47a0b39c1f7bb274b028fcf0cf418b492374019d8cb8b93c32d"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/mods"
cd "$WORK_DIR"
curl --fail --location --retry 3 --output fabric-installer.jar "$INSTALLER_URL"
echo "$INSTALLER_SHA256  fabric-installer.jar" | sha256sum --check --strict
java -jar fabric-installer.jar server -mcversion 1.20.1 -loader 0.15.0 -downloadMinecraft
curl --fail --location --retry 3 --output mods/fabric-api.jar "$FAPI_URL"
echo "$FAPI_SHA256  mods/fabric-api.jar" | sha256sum --check --strict
cp "$ROOT_DIR"/build/libs/*.jar mods/
cp "$ROOT_DIR"/ci-probe/build/libs/*.jar mods/
rm -f mods/*-sources.jar mods/*-dev.jar
printf 'eula=true\n' > eula.txt

set +e
timeout 180 java -Dmixin.debug.verbose=true -Dmixin.debug.export=true -jar fabric-server-launch.jar nogui 2>&1 | tee console.log
server_status=${PIPESTATUS[0]}
set -e

test "$server_status" -eq 0 || { echo "Fabric server exited with $server_status" >&2; exit "$server_status"; }
jq -e '.status == "PASS"' compat-result.json
! grep -Eqi 'FATAL|Mixin.*(failed|error)|crash-report' console.log
