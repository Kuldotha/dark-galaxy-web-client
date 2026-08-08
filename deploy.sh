#!/bin/sh
# Build the production distribution and publish it to GitHub Pages (the gh-pages branch).
# The branch is rebuilt from scratch via git plumbing — the working tree is never touched.
set -e
cd "$(dirname "$0")"

./gradlew wasmJsBrowserDistribution

DIST=build/dist/wasmJs/productionExecutable
touch "$DIST/.nojekyll"

export GIT_INDEX_FILE="$(mktemp -u)"
git --work-tree="$DIST" add -A
TREE=$(git write-tree)
COMMIT=$(git commit-tree "$TREE" -m "Deploy $(git rev-parse --short HEAD)")
unset GIT_INDEX_FILE

git branch -f gh-pages "$COMMIT"
git push -f origin gh-pages
echo "Deployed $COMMIT → https://kuldotha.github.io/dark-galaxy-web-client/"
