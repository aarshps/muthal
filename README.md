# Muthal

**Muthal** (Malayalam *മുതൽ*, "capital / principal") is a minimal **income & expense
ledger for institutions** — temples, churches, libraries, and similar organisations — in
the **Hora** app family. Android, iOS, and Web are developed together in this single
repository and all talk to **one** Firebase project, so you sign in on any platform and
see the same institutions and entries in real time.

One signed-in user can keep **several institutions**, each with its own ledger of
income/expense entries, a running balance, and a this-month summary.

## Repository layout

```
.
├── android/      Kotlin · Android 15+ · Material 3 Expressive · MVVM
├── ios/          SwiftUI · XcodeGen (built on CI / macOS)
├── web/          Next.js (App Router) · React · TypeScript · Tailwind v4 · PWA
└── shared/       Single-sourced contracts shared by all three platforms
    ├── firebase/   firestore.rules + firebase.json + .firebaserc (the backend contract)
    └── domain/     SPEC.md (canonical behaviour) + golden-vectors.json (parity tests)
```

Each platform keeps its own docs in its subdirectory; the project handbook lives in the
**[wiki](https://github.com/aarshps/muthal/wiki)**. Family-wide conventions, brand, and
shared source live in **[hora-core](https://github.com/aarshps/hora-core)**.

## Shared backend — one Firebase project

All clients use Firebase Auth (Google) and Cloud Firestore. The document layout is
identical across platforms:

```
users/{uid}/institutions/{instId}                       institution doc
users/{uid}/institutions/{instId}/entries/{entryId}     entry (authoritative)
users/{uid}/entries/{entryId}                           flat mirror (fast "All entries" reads)
```

Entries are **dual-written** (nested authoritative + flat mirror, same id) so the
all-institutions "All entries" view reads the mirror via a collection-group query. The
authoritative description of this behaviour — and the golden test vectors every platform
must satisfy — live in [`shared/domain/`](shared/domain/SPEC.md).

Security is enforced by Firestore rules keyed on `request.auth.uid`
([`shared/firebase/firestore.rules`](shared/firebase/firestore.rules)), not by hiding
client config. Deploy rules with:

```bash
cd shared/firebase && firebase deploy --only firestore:rules
```

## Per-platform quick start

### Web (`web/`)
```bash
cd web
npm install
cp .env.example .env.local      # fill NEXT_PUBLIC_FIREBASE_* (public by design)
npm test                         # domain-parity tests (vitest)
npm run dev                      # http://localhost:3000
npm run build
```

### Android (`android/`)
```bash
# place google-services.json in android/app/ (gitignored — Bitwarden / Firebase console)
./android/gradlew -p android :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
```
Min SDK 35 / Target 36, 100% Kotlin, Material 3 Expressive.

### iOS (`ios/`, macOS only)
```bash
cd ios && brew install xcodegen && xcodegen generate && open Muthal.xcodeproj
# place GoogleService-Info.plist in Muthal/Resources/ (gitignored)
```
SwiftUI. Bundle id `com.hora.muthal`. Built on GitHub Actions macOS runners.

## Secrets

No credentials are committed — this repo is **public**. Signing keys, keystores,
`google-services.json`, `GoogleService-Info.plist`, service-account keys, and `.env.local`
files are gitignored and fetched from Bitwarden (`scripts/bw_unlock.sh` +
`retrieve_secrets.sh`). See [`DISASTER_RECOVERY.md`](DISASTER_RECOVERY.md). Public client
config (Firebase web API keys) is not secret and is protected by Firestore rules.

## License

MIT.
