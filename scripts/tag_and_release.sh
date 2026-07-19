#!/usr/bin/env bash
#
# Files Dev — create version tags + GitHub Releases (notes pulled from CHANGELOG.md),
# and attach APKs (v3.4, v3.5, v3.6) plus a source zip to their releases.
#
# Prerequisites:
#   1. git clone https://github.com/HIZVOICE/files-dev.git && cd files-dev
#   2. gh auth login          (GitHub CLI, authenticated)
#   3. Put the APK files you have in the repo root, matching these names:
#        FilesDev-3.4-arm64.apk  FilesDev-3.4-arm32.apk
#        FilesDev-3.5-arm64.apk  FilesDev-3.5-arm32.apk
#        FilesDev-3.6-arm64.apk  FilesDev-3.6-arm32.apk
#      (Any that are missing are simply skipped.)
#   4. bash scripts/tag_and_release.sh   (run from the repo root)
#
set -uo pipefail
REPO="HIZVOICE/files-dev"
CHANGELOG="CHANGELOG.md"

# Foundation baseline commit (all pre-2.9 work landed here in the initial import).
BASE="e7cc739ea7255ee838da35206e652c5a7ede4cd5"

# version -> commit that introduced it
declare -A COMMIT=(
  [v1.0]="$BASE" [v1.1]="$BASE" [v2.0]="$BASE" [v2.5]="$BASE" [v2.8]="$BASE"
  [v2.9]=e7cc739ea7255ee838da35206e652c5a7ede4cd5
  [v3.0]=718129b67353418e4600fd8fd27ceae68e4f312a
  [v3.1]=9a1a673a35bac7aeddae9b7629392c3596ef3062
  [v3.2]=0b8baa8c8dd61a811199ae863ed58f8e2bdd3323
  [v3.3]=d2085044150c2fe16a0def73c12cf07a71d50a96
  [v3.4]=299d42b42aa408ab92d0d9162c8ae630cc21d27b
  [v3.5]=dbb273b2084658e1c67de573a26c69539208abe1
  [v3.6]=c065190a02262de83399408b35c7090591797b8a
)
# version -> CHANGELOG.md section header key
declare -A CLKEY=(
  [v1.0]="2.8 and earlier" [v1.1]="2.8 and earlier" [v2.0]="2.8 and earlier"
  [v2.5]="2.8 and earlier" [v2.8]="2.8 and earlier"
  [v2.9]="2.9" [v3.0]="3.0" [v3.1]="3.1" [v3.2]="3.2"
  [v3.3]="3.3" [v3.4]="3.4" [v3.5]="3.5" [v3.6]="3.6"
)
# version -> APK assets to attach (space separated)
declare -A APKS=(
  [v3.4]="FilesDev-3.4-arm64.apk FilesDev-3.4-arm32.apk"
  [v3.5]="FilesDev-3.5-arm64.apk FilesDev-3.5-arm32.apk"
  [v3.6]="FilesDev-3.6-arm64.apk FilesDev-3.6-arm32.apk"
)
ORDER=(v1.0 v1.1 v2.0 v2.5 v2.8 v2.9 v3.0 v3.1 v3.2 v3.3 v3.4 v3.5 v3.6)

# Print the CHANGELOG.md section whose header is "## [<key>]" up to the next "## [".
section_for() {
  awk -v h="## [$1]" '
    substr($0,1,length(h))==h { p=1; print; next }
    p && /^## \[/ { exit }
    p { print }
  ' "$CHANGELOG"
}

echo "==> Fetching refs/tags"
git fetch --all --tags --quiet

for v in "${ORDER[@]}"; do
  echo "==> Tag $v -> ${COMMIT[$v]}"
  git tag -a "$v" -m "Files Dev $v" "${COMMIT[$v]}" 2>/dev/null || echo "   ($v exists — skipping)"
done
echo "==> Pushing tags"
git push origin --tags

echo "==> Creating releases (notes from $CHANGELOG)"
for v in "${ORDER[@]}"; do
  if gh release view "$v" --repo "$REPO" >/dev/null 2>&1; then
    echo "   ($v release exists — skipping)"; continue
  fi
  notes="$(mktemp)"
  { echo "## Files Dev $v"; echo; section_for "${CLKEY[$v]}"; } > "$notes"

  assets=()
  for apk in ${APKS[$v]:-}; do
    if [[ -f "$apk" ]]; then assets+=("$apk"); else echo "   WARN: $apk missing — not attached"; fi
  done

  latest="false"
  if [[ "$v" == "v3.6" ]]; then
    latest="true"
    # Source snapshot of the tagged tree as a release asset.
    zip="FilesDev-3.6-src.zip"
    if git rev-parse "v3.6" >/dev/null 2>&1; then
      git archive --format=zip -o "$zip" v3.6 && assets+=("$zip")
    fi
  fi

  echo "==> Release $v  (assets: ${assets[*]:-none})"
  gh release create "$v" --repo "$REPO" \
    --title "Files Dev $v" \
    --notes-file "$notes" \
    --latest="$latest" \
    "${assets[@]}"
  rm -f "$notes"
done

echo "==> Done: https://github.com/${REPO}/releases"
