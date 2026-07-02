#!/bin/bash
# Retrieve Muthal build secrets from Bitwarden into the gitignored local files the
# build needs. Run from android/:  source ../scripts/bw_unlock.sh && ./retrieve_secrets.sh
# (or rely on the .env.local BW_PASSWORD fallback below). See DISASTER_RECOVERY.md.

if [ -f .env.local ]; then
    set -a
    # shellcheck disable=SC1091
    . .env.local
    set +a
fi

if [ -z "$BW_SESSION" ]; then
    if [ -n "$BW_PASSWORD" ]; then
        echo "Unlocking Bitwarden non-interactively (BW_PASSWORD)..."
        export BW_SESSION=$(bw unlock --passwordenv BW_PASSWORD --raw)
    else
        echo "Enter your Bitwarden master password to unlock the vault."
        export BW_SESSION=$(bw unlock --raw)
    fi
fi
[ -z "$BW_SESSION" ] && { echo "Error: could not unlock Bitwarden." >&2; exit 1; }

bw sync
ITEM_ID=$(bw list items --search Muthal | jq -r '.[] | select(.name=="Muthal") | .id' | head -1)
[ -z "$ITEM_ID" ] && { echo "Error: no vault item named exactly 'Muthal'." >&2; exit 1; }
ITEM_JSON=$(bw get item "$ITEM_ID")
# Fail LOUDLY if a field is missing/empty — a silent empty value here writes a
# 0-byte secret file and produces baffling downstream errors (learned 2026-07-02
# when play_console_key.json was absent from the vault).
field() {
    local v
    v=$(echo "$ITEM_JSON" | jq -r --arg n "$1" '.fields[] | select(.name==$n).value')
    if [ -z "$v" ] || [ "$v" = "null" ]; then
        echo "Error: vault field '$1' is missing or empty on the Muthal item." >&2
        exit 1
    fi
    echo "$v"
}

echo "Restoring google-services.json..."
echo "$(field 'google-services.json [Part 1]')$(field 'google-services.json [Part 2]')" | base64 --decode > app/google-services.json

echo "Restoring GoogleService-Info.plist (iOS)..."
field 'GoogleService-Info.plist [base64]' | base64 --decode > ../ios/Muthal/Resources/GoogleService-Info.plist

echo "Restoring upload keystore..."
echo "$(field 'muthal-upload-key [Part 1]')$(field 'muthal-upload-key [Part 2]')" | base64 --decode > muthal-upload-key

echo "Restoring Play Console service-account key..."
echo "$(field 'play_console_key.json [Part 1]')$(field 'play_console_key.json [Part 2]')" | base64 --decode > app/play_console_key.json

echo "Writing signing properties to local.properties..."
if ! grep -q "RELEASE_STORE_PASSWORD" local.properties 2>/dev/null; then
    {
        echo "RELEASE_STORE_FILE=../muthal-upload-key"
        echo "RELEASE_STORE_PASSWORD=$(field 'Keystore Password')"
        echo "RELEASE_KEY_ALIAS=$(field 'Key Alias')"
        echo "RELEASE_KEY_PASSWORD=$(field 'Key Password')"
    } >> local.properties
fi

echo "✅ Muthal secrets restored."
