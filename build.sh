#!/usr/bin/env bash
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
zombie_buddy_jar=${ZOMBIE_BUDDY_JAR:-/home/cjstorrs/Zomboid/mods/ZombieBuddy/libs/ZombieBuddy.jar}
build_dir="$root_dir/build"
main_classes="$build_dir/classes/main"
test_classes="$build_dir/classes/test"
output_jar="$root_dir/42/media/java/CJSFirearmDamageChanceFix.jar"

if [ ! -f "$zombie_buddy_jar" ]; then
    echo "ZombieBuddy JAR not found: $zombie_buddy_jar" >&2
    exit 1
fi

mkdir -p "$main_classes" "$test_classes" "$(dirname -- "$output_jar")"
find "$main_classes" "$test_classes" -type f -name '*.class' -delete

mapfile -d '' main_sources < <(find "$root_dir/src/main/java" -type f -name '*.java' -print0 | sort -z)
if [ "${#main_sources[@]}" -eq 0 ]; then
    echo "No main Java sources found" >&2
    exit 1
fi

javac --release 17 -cp "$zombie_buddy_jar" -d "$main_classes" "${main_sources[@]}"
jar --create --file "$output_jar" --date=2000-01-01T00:00:00Z -C "$main_classes" .

mapfile -d '' test_sources < <(find "$root_dir/src/test/java" -type f -name '*.java' -print0 | sort -z)
if [ "${#test_sources[@]}" -gt 0 ]; then
    javac --release 17 -cp "$main_classes:$zombie_buddy_jar" -d "$test_classes" "${test_sources[@]}"
    java -ea -cp "$test_classes:$main_classes:$zombie_buddy_jar" com.cjstorrs.firearmdamagechancefix.DamageChancePatchTest
fi

echo "Built: $output_jar"
