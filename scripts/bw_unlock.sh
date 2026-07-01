#!/usr/bin/env bash
# bw_unlock.sh — unlock Bitwarden and export BW_SESSION.
#
# Source this script to inherit BW_SESSION in the calling shell:
#   source scripts/bw_unlock.sh
#
# From a subdirectory (e.g. android/):
#   source ../scripts/bw_unlock.sh
#
# The script reads credentials from .env at the repo root:
#   BW_CLIENTID, BW_CLIENTSECRET, BW_PASSWORD
# Copy .env.example → .env and fill in the values before first use.
# See DISASTER_RECOVERY.md §ADR-001 for the design rationale.
#
# Idempotent: login is skipped when already authenticated; unlock is skipped
# when BW_SESSION is already set and the vault reports "unlocked".

set -euo pipefail

_BW_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_BW_ENV_FILE="${_BW_SCRIPT_DIR}/../.env"

if [ -f "$_BW_ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    . "$_BW_ENV_FILE"
    set +a
fi

# Fail fast with a clear message if any required variable is missing.
: "${BW_CLIENTID:?BW_CLIENTID not set — copy .env.example → .env (see DISASTER_RECOVERY.md §ADR-001)}"
: "${BW_CLIENTSECRET:?BW_CLIENTSECRET not set — copy .env.example → .env}"
: "${BW_PASSWORD:?BW_PASSWORD not set — copy .env.example → .env}"

_bw_status() {
    bw status 2>/dev/null | jq -r '.status // "unauthenticated"' 2>/dev/null \
        || echo "unauthenticated"
}

_BW_CURRENT_STATUS=$(_bw_status)

# Login via API key if not authenticated.
if [ "$_BW_CURRENT_STATUS" = "unauthenticated" ]; then
    printf 'bw_unlock: logging in via API key...\n' >&2
    BW_CLIENTID="$BW_CLIENTID" BW_CLIENTSECRET="$BW_CLIENTSECRET" \
        bw login --apikey >&2
    _BW_CURRENT_STATUS=$(_bw_status)
fi

# Unlock the vault if locked (or if BW_SESSION is not yet set in this shell).
if [ "$_BW_CURRENT_STATUS" = "unlocked" ] && [ -n "${BW_SESSION:-}" ]; then
    printf 'bw_unlock: vault already unlocked, BW_SESSION already set.\n' >&2
else
    printf 'bw_unlock: unlocking vault...\n' >&2
    BW_SESSION=$(bw unlock --passwordenv BW_PASSWORD --raw)
    export BW_SESSION
    printf 'bw_unlock: vault unlocked.\n' >&2
fi

unset _BW_SCRIPT_DIR _BW_ENV_FILE _BW_CURRENT_STATUS
