#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
WORK_DIR="$ROOT_DIR/.ci-fabric-1211-server"
MAIN_ARTIFACT_DIR="$ROOT_DIR/ci-artifacts/main"
PROBE_ARTIFACT_DIR="$ROOT_DIR/ci-artifacts/probe"
INSTALLER_URL="https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar"
INSTALLER_SHA256="62edf170bdcc41edea85d33acf3eb85474258699b3d41f9418d286c836cb088d"
FAPI_URL="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.7%2B1.21.1/fabric-api-0.116.7%2B1.21.1.jar"
FAPI_SHA256="08018cc48c97415a38016a00dbd5a2c7a460ba6b7a051690a8cb3dbb5e8482a4"

mapfile -t main_jars < <(find "$MAIN_ARTIFACT_DIR" -maxdepth 1 -type f -name 'tinkerers-smithing-*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
mapfile -t probe_jars < <(find "$PROBE_ARTIFACT_DIR" -maxdepth 1 -type f -name 'tinkerers-smithing-ci-probe-*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
test "${#main_jars[@]}" -eq 1
test "${#probe_jars[@]}" -eq 1

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/mods"
cd "$WORK_DIR"
curl --fail --location --retry 3 --output fabric-installer.jar "$INSTALLER_URL"
echo "$INSTALLER_SHA256  fabric-installer.jar" | sha256sum --check --strict
java -jar fabric-installer.jar server -mcversion 1.21.1 -loader 0.15.11 -downloadMinecraft
curl --fail --location --retry 3 --output mods/fabric-api-0.116.7+1.21.1.jar "$FAPI_URL"
echo "$FAPI_SHA256  mods/fabric-api-0.116.7+1.21.1.jar" | sha256sum --check --strict
cp "${main_jars[0]}" mods/
cp "${probe_jars[0]}" mods/
printf 'eula=true\n' > eula.txt

set +e
timeout 300 java -Dmixin.debug.verbose=true -Dmixin.debug.export=true -jar fabric-server-launch.jar nogui 2>&1 | tee console.log
server_status=${PIPESTATUS[0]}
set -e

mapfile -t exported_anvil_handlers < <(find "$WORK_DIR/.mixin.out" -type f \( -path '*/net/minecraft/class_1706.class' -o -path '*/net/minecraft/screen/AnvilScreenHandler.class' \) -print 2>/dev/null | sort)
test "${#exported_anvil_handlers[@]}" -ge 1
cp "${exported_anvil_handlers[0]}" fabric-exported-anvil-screen-handler.class
javap -p -c -v fabric-exported-anvil-screen-handler.class > fabric-exported-anvil-screen-handler.javap.txt

test "$server_status" -eq 0 || { echo "Fabric 1.21.1 服务端异常退出：$server_status" >&2; exit "$server_status"; }
jq -e '.status == "PASS"' compat-result.json
grep -Fq 'TS_ANVIL_1211_RESULT status=PASS' console.log
grep -Eqi 'Stopping (the )?server' console.log
if grep -Eqi 'FATAL|InvalidInjectionException|MixinApplyError|Mixin apply failed' console.log; then
	echo "Fabric 日志出现 Mixin/FATAL 异常" >&2
	exit 1
fi
if [[ -d crash-reports ]] && find crash-reports -type f -name '*.txt' -print -quit | grep -q .; then
	echo "Fabric 生成了 crash-report" >&2
	exit 1
fi
