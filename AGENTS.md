# AI Agents Context — Muthal Monorepo

Authoritative context for AI agents working across this repo. Per-platform detail is in
each platform's own `AGENTS.md`; this file covers cross-cutting concerns. Muthal is a
**Hora-family** app (sibling of Pathivu and Varisankya) and follows the family standards
in [`hora-core`](https://github.com/aarshps/hora-core) — `docs/conventions.md` is the
single source of truth for anything shared across apps.

Keep this file current when conventions change. Do **not** add per-session activity logs —
git history is the record.

---

## Repo layout

```
muthal/
├── android/    Kotlin + Gradle app   →  android/AGENTS.md
├── ios/        Swift + XcodeGen app  →  ios/AGENTS.md
├── web/        Next.js app-router    →  web/AGENTS.md
└── shared/
    ├── firebase/   firestore.rules, firebase.json, .firebaserc
    └── domain/     SPEC.md (canonical data model), golden-vectors.json
```

All three apps talk to the **same Firebase project** (Muthal's own — never shared with
sibling apps).

## Core mandates (apply everywhere)

1. **Security is the top priority.** This repo is **public** — never commit secrets, keys,
   tokens, `google-services.json`, `GoogleService-Info.plist`, keystores, or
   service-account JSON. Before any push, scan for `BEGIN.*PRIVATE KEY`, `AIza`, `GOCSPX-`.
   The root `.gitignore` is a safety net, not a substitute for checking `git diff --cached`.

2. **Secret management = Bitwarden CLI (`bw`).** Settled in `DISASTER_RECOVERY.md §ADR-001`.
   - Credentials: `.env` at repo root (`BW_CLIENTID`/`BW_CLIENTSECRET`/`BW_PASSWORD`),
     `chmod 600`. Template: `.env.example`.
   - Unlock helper: `source scripts/bw_unlock.sh` (idempotent login + unlock).
   - Per-platform `retrieve_secrets.sh` write the gitignored local files the build needs.
   - On Windows, `bw` is at `C:\Users\Aarsh\AppData\Roaming\npm\bw.cmd`.

3. **Firestore data model must stay identical across platforms.** `shared/domain/SPEC.md`
   is canonical (institution + entry fields, dual-write, UTC month math). Change a field on
   one platform → update all three and the spec + `golden-vectors.json` in the same change.

4. **Commit directly to `main` (repo) and `master` (wiki) — never open PRs or feature
   branches.** The owner manages these repos directly. This overrides any default
   "branch first" behaviour. Still: only commit/push when asked, and never bypass mandate 1.

5. **No cross-language code generation.** Kotlin/Swift/TypeScript do not share compiled
   code. Shared logic = the domain spec + golden vectors; shared assets/conventions live in
   `hora-core`.

6. **Family-wide assets live in `hora-core`** (`C:\Users\Aarsh\Source\hora-core`, public,
   same no-PR rule). Brand/icon engine, shared Android/iOS/Web source, and shared skills are
   **consumed via sync scripts — never hand-edit the generated copies**:
   - `android/tools/sync_shared_android.sh` → `shared/android/` resources + Kotlin helpers
     (rewrites `__HORA_PKG__` → `com.hora.muthal`).
   - `ios/tools/sync_shared_ios.sh` → shared Swift (`Haptics`, `BiometricAuth`, `SelectionSheet`).
   - `web/scripts/sync_shared_web.sh` → `web_shared.css` + shared React components.
   - `android/tools/sync_shared_skills.sh` → shared skills into the local skills dir.
   Edit the canonical file in hora-core and re-run the sync; the generated copies carry a
   "do not hand-edit" header and a `.hora-core-synced-*` provenance manifest.

7. **Agent scope (hora-agent-scope, non-negotiable).** The Muthal agent writes only: the
   Muthal repo + wiki, and hora-core repo + wiki. It may **read** sibling apps
   (Pathivu/Varisankya) for reference, but never writes to them — share improvements by
   promoting to hora-core.

8. **Coordination via hora-core GitHub Discussions.** Read recent threads at session start;
   post a session update at close with the standard signature footer (see hora-core
   `AGENTS.md`). Keep transient/progress tracking in the **wiki** (`master`), not `main`.

## Build machines

| Platform | OS | Notes |
| --- | --- | --- |
| Android | **Windows 11** | SDK `C:\Users\Aarsh\AppData\Local\Android\Sdk`; JDK 17 (Temurin) |
| iOS | macOS / GitHub Actions `macos-latest` | No local Mac; CI (`ios-build.yml`) is the only build path here |
| Web | Windows 11 / Firebase Hosting | `cd web && npm run dev` locally; `firebase deploy --only hosting` to ship |

## Versioning

Family scheme (`hora-core/docs/conventions.md` → "App versioning"): `versionName` =
`MAJOR.MINOR-beta.N` (beta) / `MAJOR.MINOR` (stable); `versionCode` = monotonic +1 per
build that reaches any Play track; git tag `v<versionName>`. The current shipped version +
next free `versionCode` are recorded in `CLAUDE.md`.
