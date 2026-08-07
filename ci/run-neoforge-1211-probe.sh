#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
WORK_DIR="$ROOT_DIR/.ci-neoforge-1211-server"
MAIN_ARTIFACT_DIR="$ROOT_DIR/ci-artifacts/main"
PROBE_ARTIFACT_DIR="$ROOT_DIR/ci-artifacts/probe"
INSTALLER_URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.226/neoforge-21.1.226-installer.jar"
INSTALLER_SHA256="7ad8225fa9959aa155e7ff3d68e126c81d50bdd7d0e224f460cf89af5468bfdb"
CONNECTOR_URL="https://github.com/Sinytra/Connector/releases/download/2.0.0-beta.14%2B1.21.1/connector-2.0.0-beta.14%2B1.21.1-full.jar"
CONNECTOR_SHA256="47800c73034130a730d119dd4ffb935b0cde7061ee196bc9c9dc7e75ac52e53d"
FFAPI_URL="https://github.com/Sinytra/ForgifiedFabricAPI/releases/download/0.116.7%2B2.2.4%2B1.21.1/forgified-fabric-api-0.116.7%2B2.2.4%2B1.21.1.jar"
FFAPI_SHA256="431bb9f530d46f240a7c2fe3cd44f7a222f6ce1d01bd1595ecb8f7513c21f906"

mapfile -t main_jars < <(find "$MAIN_ARTIFACT_DIR" -maxdepth 1 -type f -name 'tinkerers-smithing-*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
mapfile -t probe_jars < <(find "$PROBE_ARTIFACT_DIR" -maxdepth 1 -type f -name 'tinkerers-smithing-ci-probe-*.jar' ! -name '*-sources.jar' ! -name '*-dev.jar' | sort)
test "${#main_jars[@]}" -eq 1
test "${#probe_jars[@]}" -eq 1

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/mods"
cd "$WORK_DIR"
curl --fail --location --retry 3 --output neoforge-installer.jar "$INSTALLER_URL"
echo "$INSTALLER_SHA256  neoforge-installer.jar" | sha256sum --check --strict
java -jar neoforge-installer.jar --installServer
curl --fail --location --retry 3 --output mods/connector-2.0.0-beta.14+1.21.1-full.jar "$CONNECTOR_URL"
echo "$CONNECTOR_SHA256  mods/connector-2.0.0-beta.14+1.21.1-full.jar" | sha256sum --check --strict
curl --fail --location --retry 3 --output mods/forgified-fabric-api-0.116.7+2.2.4+1.21.1.jar "$FFAPI_URL"
echo "$FFAPI_SHA256  mods/forgified-fabric-api-0.116.7+2.2.4+1.21.1.jar" | sha256sum --check --strict
cp "${main_jars[0]}" mods/
cp "${probe_jars[0]}" mods/
printf 'eula=true\n' > eula.txt
printf '%s\n' '-Dmixin.debug.verbose=true' '-Dmixin.debug.export=true' '-Dconnector.logging.markers=MIXINPATCH,MERGER' >> user_jvm_args.txt

set +e
timeout 300 ./run.sh nogui 2>&1 | tee console.log
server_status=${PIPESTATUS[0]}
set -e

# Connector 在启动时将 Fabric jar 放入这个真实转换缓存；保留其 refmap 和类常量池供红绿证据检查。
mapfile -t transformed_jars < <(find "$WORK_DIR/mods/.connector" -type f -name '*.jar' -print 2>/dev/null | sort)
printf '%s\n' "${transformed_jars[@]}" > connector-transformed-jars.txt
transformed_mixin_jar=""
for candidate in "${transformed_jars[@]}"; do
	if unzip -Z1 "$candidate" | grep -qx 'folk/sisby/tinkerers_smithing/mixin/AnvilScreenHandlerMixin.class'; then
		transformed_mixin_jar="$candidate"
		break
	fi
done
test -n "$transformed_mixin_jar"
unzip -p "$transformed_mixin_jar" tinkerers-smithing-refmap.json > connector-transformed-refmap.json
javap -classpath "$transformed_mixin_jar" -v folk.sisby.tinkerers_smithing.mixin.AnvilScreenHandlerMixin > connector-transformed-anvil-mixin.javap.txt

test "$server_status" -eq 0 || { echo "NeoForge 1.21.1 服务端异常退出：$server_status" >&2; exit "$server_status"; }
jq -e '.status == "PASS"' compat-result.json
grep -Fq 'TS_ANVIL_1211_RESULT status=PASS' console.log
grep -Eqi 'Stopping (the )?server' console.log
if grep -Eqi 'FATAL|InvalidInjectionException|MixinApplyError|Mixin apply failed' console.log; then
	echo "NeoForge 日志出现 Mixin/FATAL 异常" >&2
	exit 1
fi
if [[ -d crash-reports ]] && find crash-reports -type f -name '*.txt' -print -quit | grep -q .; then
	echo "NeoForge 生成了 crash-report" >&2
	exit 1
fi
