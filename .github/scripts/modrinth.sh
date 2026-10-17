#!/usr/bin/env bash
# One Modrinth version per jar, named like 0.2.0+26.2-fabric. DRY_RUN=1 prints the payloads instead.
set -euo pipefail
jars="$1" toml="$2" changelog="$(cat "$3")"
fabric_deps='[{"project_id":"P7dR8mSH","dependency_type":"required"},{"project_id":"Ha28R6CL","dependency_type":"required"}]'
kff_deps='[{"project_id":"ordsPcFz","dependency_type":"required"}]'

for path in "$jars"/steamed-*.jar; do
  jar="$(basename "$path")"                 # steamed-fabric-0.2.0+26.2.jar
  rest="${jar#steamed-}"; loader="${rest%%-*}"
  rest="${rest#"$loader"-}"; rest="${rest%.jar}"
  version="${rest%%+*}"; mc="${rest#*+}"
  game_versions="$(python3 -c 'import json, sys, tomllib
print(json.dumps(tomllib.load(open(sys.argv[1], "rb"))[sys.argv[2]]["mod"]["mc_releases"]))' "$toml" "$mc")"
  deps="$fabric_deps"; [ "$loader" = neoforge ] || [ "$loader" = forge ] && deps="$kff_deps"
  data="$(jq -n --arg v "$version+$mc-$loader" --arg l "$loader" --argjson gv "$game_versions" --arg c "$changelog" \
    --argjson deps "$deps" '{
      project_id: "SZuvIr1Y", name: "Steamed \($v)", version_number: $v, changelog: $c,
      version_type: "beta", loaders: [$l], game_versions: $gv, featured: false,
      dependencies: $deps, file_parts: ["file"], primary_file: "file"}')"
  if [ "${DRY_RUN:-}" = 1 ]; then echo "$jar -> $(jq -c 'del(.changelog)' <<<"$data")"; continue; fi
  curl --fail-with-body -sS -X POST https://api.modrinth.com/v2/version \
    -H "Authorization: $MODRINTH_TOKEN" -H "User-Agent: 0x00ACAB/steamed (GitHub Actions)" \
    --form-string "data=$data" -F "file=@$path;type=application/java-archive;filename=$jar"
  echo
done
