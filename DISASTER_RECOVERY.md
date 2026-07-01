# Muthal — Disaster Recovery

> **Recovery acceptance test:** on a fresh machine with only the Bitwarden
> master password, `git clone` → read this file → `bw login`/`bw unlock` →
> `source scripts/bw_unlock.sh` → per-platform `retrieve_secrets.sh` should
> recover every Muthal secret. No other tribal knowledge required.

Muthal follows the **Hora-family** secret-management convention
(`hora-core/docs/conventions.md` → "Secrets" / "Disaster-recovery"). Nothing
secret is ever committed — this repo is **public**.

---

## ADR-001: Bitwarden master password is the recovery seed (SETTLED)

**Decision:** The Bitwarden master password — held by the developer offline
(memory, physical safe, or equivalent) — is the single recovery seed for all
Muthal secrets. The `.env` file at the repo root stores `BW_CLIENTID`,
`BW_CLIENTSECRET`, and `BW_PASSWORD` solely for agent/CI ergonomics so that
automated scripts can unlock the vault non-interactively.

| Factor | Detail |
| --- | --- |
| Agent ergonomics | Scripts run headlessly without a human typing a password each time. |
| Single-factor-on-disk risk | `.env` holds the master password in plaintext on disk. |
| Mitigations | `chmod 600 .env`; full-disk encryption (FDE) on the dev machine; master password also held offline. |
| Accepted tradeoff | Agent ergonomics > single-factor-on-disk risk, given FDE + offline master password. |

**For future agents:** Do not re-raise the "should `.env` store the master
password?" question — settled family-wide. File a GitHub issue if a genuinely new
risk changes the calculus. **See also:** `scripts/bw_unlock.sh`, `.env.example`.

---

## Secret Inventory

All secrets live under **Bitwarden folder `Hora`**. "Item name" is the exact
string used with `bw get item "<name>"`.

### Android — Bitwarden item `Muthal`

| Secret | Field | Notes |
| --- | --- | --- |
| `google-services.json` | `google-services.json [Part 1]` + `[Part 2]` | Base64, split for field-size limit. Place at `android/app/google-services.json`. |
| Android upload keystore | `muthal-upload-key [Part 1]` + `[Part 2]` | Base64 `.jks`. Place at `android/muthal-upload-key`. |
| Play Console service account | `play_console_key.json` | Base64 JSON. Place at `android/app/play_console_key.json`. |
| Keystore alias | `Key Alias` | Plain text → `RELEASE_KEY_ALIAS` in `android/local.properties`. |
| Keystore password | `Keystore Password` | Plain text → `RELEASE_STORE_PASSWORD`. |
| Key password | `Key Password` | Plain text → `RELEASE_KEY_PASSWORD`. |
| `GoogleService-Info.plist` | `GoogleService-Info.plist [base64]` | Base64. Place at `ios/Muthal/Resources/GoogleService-Info.plist`. |

Automated retrieval:
```bash
source scripts/bw_unlock.sh
cd android && ./retrieve_secrets.sh
```

### iOS — Bitwarden item `Muthal iOS signing` (create after Apple enrollment)

| Secret | Field | Status |
| --- | --- | --- |
| Distribution private key | `Muthal-key.pem [base64]` | TODO — add after enrollment |
| Distribution certificate (.p12) | `Muthal-Distribution.p12 [base64]` | TODO |
| .p12 export password | `P12_PASSWORD` | TODO |
| Provisioning profile | `Muthal_AppStore.mobileprovision [base64]` | TODO |
| App Store Connect API key | `AuthKey_p8 [base64]` | TODO |
| Team / Issuer / Key IDs | `APPLE_TEAM_ID`, `APPLE_API_ISSUER_ID`, `APPLE_API_KEY_ID` | TODO |

iOS builds run on GitHub Actions **macOS** runners (this is a Windows dev box).
CI reads the same materials as GitHub Secrets: `GOOGLE_SERVICE_INFO_BASE64`,
`APPLE_TEAM_ID`, `APPLE_API_ISSUER_ID`, `APPLE_API_KEY_ID`, `APPLE_API_KEY_BASE64`,
`BUILD_CERTIFICATE_BASE64`, `P12_PASSWORD`, `PROVISIONING_PROFILE_BASE64`,
`KEYCHAIN_PASSWORD`.

### Web — Bitwarden item `Muthal web .env.local` (secure note)

Firebase web config (the `web/.env.local` contents — `NEXT_PUBLIC_FIREBASE_*`).
Restore with `bw get notes "Muthal web .env.local" > web/.env.local`.

### Firebase

Project: **`hora-muthal`** (project number `310482235422`).
Console: https://console.firebase.google.com/project/hora-muthal · Auth: Google (enabled) ·
Firestore rules: `shared/firebase/firestore.rules` (source-controlled, deployed). Each Hora
app has its **own** Firebase project — never shared with Pathivu/Varisankya.

### Accounts

| Service | Account | Notes |
| --- | --- | --- |
| GitHub | `aarshps` → `aarshps/muthal` (public) | |
| Google Play Console | `aarshps@gmail.com` | New app → 12-testers × 14-day gate before production. |
| Apple Developer | `aarshps@gmail.com` | App record + signing created during iOS release. |
| Firebase / Google | `aarshps@gmail.com` | Controls Firebase + Play. |
| Web hosting | Firebase Hosting → https://hora-muthal.web.app (project `hora-muthal`) | |

---

## Recovery Runbook — Lost dev machine (primary scenario)

```bash
# 1. Prereqs: git, bw CLI, jq, JDK 17, Android SDK.
# 2. Clone
git clone https://github.com/aarshps/muthal.git && cd muthal
# 3. Bitwarden login (master password is the seed)
bw login aarshps@gmail.com
# 4. Create .env from the API key (vault.bitwarden.com → Settings → Security → API key)
cp .env.example .env && chmod 600 .env   # fill BW_CLIENTID/SECRET/PASSWORD
# 5. Restore Android + iOS configs
source scripts/bw_unlock.sh
cd android && ./retrieve_secrets.sh
#    writes android/app/google-services.json, android/muthal-upload-key,
#    android/app/play_console_key.json, signing props appended to local.properties,
#    and ios/Muthal/Resources/GoogleService-Info.plist
# 6. Restore web config
cd .. && source scripts/bw_unlock.sh && bw get notes "Muthal web .env.local" > web/.env.local && chmod 600 web/.env.local
```

Other scenarios (lost Bitwarden / Apple / GitHub / Play / Firebase) follow the
same family playbook as the sibling apps' DR docs: Bitwarden is the vault, the
master password is the seed; Firebase/Play/Apple are all recoverable through the
`aarshps@gmail.com` account; the Android upload key, if lost, can be re-requested
via Play Console → App Integrity (the installed app keeps working — Play holds the
final signing key).

---

## Status / Open TODOs

- [x] Firebase project `hora-muthal` created; `.firebaserc` + configs point at it.
- [x] Upload keystore generated (`keytool`) and the `Muthal` Bitwarden item created with all
      Android fields + `GoogleService-Info.plist [base64]` + the web `.env.local` (as a field).
- [ ] Create `Muthal iOS signing` Bitwarden item + the iOS GitHub Secrets **after Apple enrolment**.
- [ ] Add the Play Console **publisher service-account key** to the `Muthal` item as
      `play_console_key.json` (base64) once created (then `retrieve_secrets.sh` restores it).
- [ ] Independent offline backup of the Android upload keystore (encrypted USB).
- [ ] Bitwarden Emergency Access designee + 2FA recovery codes stored offline.
- [ ] Enable Firestore scheduled exports to Cloud Storage (guards against accidental project deletion).
- [ ] Monthly encrypted `bw export` to offline storage.
