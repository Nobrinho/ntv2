#!/usr/bin/env bash
# Publica uma nova versão: sobe versionName/versionCode, commita, cria a tag e envia.
# O push da tag v* dispara o workflow "Publicar atualização do NTV" (.github/workflows/release.yml).
#
# Uso:
#   scripts/release.sh            -> próxima patch (0.4.3 -> 0.4.4)
#   scripts/release.sh minor      -> próxima minor (0.4.3 -> 0.5.0)
#   scripts/release.sh major      -> próxima major (0.4.3 -> 1.0.0)
#   scripts/release.sh 0.6.1      -> versão exata
#   scripts/release.sh --dry-run  -> só mostra o que faria (combina com os demais)
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
GRADLE_FILE="app/build.gradle.kts"

DRY_RUN=false
BUMP="patch"
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) BUMP="$arg" ;;
  esac
done

fail() { echo "Erro: $*" >&2; exit 1; }

# --- Pré-condições -----------------------------------------------------------------------------
BRANCH=$(git rev-parse --abbrev-ref HEAD)
[ "$BRANCH" = "main" ] || fail "rode na branch main (atual: $BRANCH)."
if ! $DRY_RUN; then
  git diff --quiet && git diff --cached --quiet || fail "há alterações não commitadas. Commite ou descarte antes."
fi
git fetch --quiet --tags origin
[ "$(git rev-list --count HEAD..origin/main)" = "0" ] || fail "sua main está atrás da origin/main. Faça git pull antes."

# --- Versões atuais ----------------------------------------------------------------------------
CURRENT_NAME=$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' "$GRADLE_FILE" | head -1)
CURRENT_CODE=$(sed -n 's/^[[:space:]]*versionCode = \([0-9]*\)/\1/p' "$GRADLE_FILE" | head -1)
[ -n "$CURRENT_NAME" ] && [ -n "$CURRENT_CODE" ] || fail "não achei versionName/versionCode em $GRADLE_FILE."

IFS=. read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"
case "$BUMP" in
  patch) NEW_NAME="$MAJOR.$MINOR.$((PATCH + 1))" ;;
  minor) NEW_NAME="$MAJOR.$((MINOR + 1)).0" ;;
  major) NEW_NAME="$((MAJOR + 1)).0.0" ;;
  [0-9]*.[0-9]*.[0-9]*) NEW_NAME="$BUMP" ;;
  *) fail "argumento inválido: $BUMP (use patch, minor, major ou X.Y.Z)." ;;
esac
NEW_CODE=$((CURRENT_CODE + 1))
TAG="v$NEW_NAME"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && fail "a tag $TAG já existe."

echo "Versão:  $CURRENT_NAME ($CURRENT_CODE) -> $NEW_NAME ($NEW_CODE)"
echo "Tag:     $TAG"
if $DRY_RUN; then
  echo "(dry-run: nada foi alterado)"
  exit 0
fi

read -r -p "Publicar $TAG? [s/N] " ANSWER
[[ "$ANSWER" =~ ^[sS]$ ]] || { echo "Cancelado."; exit 1; }

# --- Sobe a versão, commita, cria a tag e envia ----------------------------------------------
sed -i \
  -e "s/^\([[:space:]]*versionName = \)\".*\"/\1\"$NEW_NAME\"/" \
  -e "s/^\([[:space:]]*versionCode = \)[0-9]*/\1$NEW_CODE/" \
  "$GRADLE_FILE"

git add "$GRADLE_FILE"
git commit -m "chore(release): $NEW_NAME"
git tag -a "$TAG" -m "NTV $NEW_NAME"
git push origin main
git push origin "$TAG"

REPO_URL=$(git remote get-url origin | sed -e 's#git@github.com:#https://github.com/#' -e 's#\.git$##')
echo
echo "Pronto: $TAG enviada. Acompanhe o workflow em $REPO_URL/actions"
