# Muthal — Canonical Domain Spec

This is the **single source of truth** for Muthal's cross-platform behaviour. The Android
(Kotlin), iOS (Swift), and Web (TypeScript) clients each re-implement this logic natively;
they must all agree, because they read and write the **same** Firestore documents in
Muthal's dedicated Firebase project. The machine-checkable cases live in
[`golden-vectors.json`](golden-vectors.json) — every platform runs them.

Muthal is a *minimal income & expense ledger for institutions* (temples, churches,
libraries, …). An institution can be shared by **multiple signed-in users**, each with a
**role** that controls what they can see and do.

| Concern | Web | Android | iOS |
| --- | --- | --- | --- |
| Institution / member / category model | `web/lib/types.ts` | `app/.../model/Models.kt` | `Muthal/Models/Models.swift` |
| Entry model | `web/lib/types.ts` | `app/.../model/Models.kt` | `Muthal/Models/Models.swift` |
| Currency | `web/lib/currency.ts` | `app/.../util/CurrencyHelper.kt` | `Muthal/Models/Currency.swift` |
| Summary / "this month" / period export | `web/lib/summary.ts` | `app/.../util/SummaryHelper.kt` | `Muthal/Models/Summary.swift` |
| Categories | `web/lib/categories.ts` | `app/.../util/Categories.kt` | `Muthal/Models/Categories.swift` |
| Persistence | `web/lib/firestore.ts` | `app/.../data/FirestoreRepo.kt` | `Muthal/Services/FirestoreService.swift` |

## 1. Data model (v2 — multi-user institutions)

```
institutions/{instId}                                   ← institution document (AUTHORITATIVE, top-level)
institutions/{instId}/members/{uid}                      ← membership + role for one user
institutions/{instId}/categories/{categoryId}             ← per-institution category (income or expense)
institutions/{instId}/entries/{entryId}                   ← entry (AUTHORITATIVE)

institutionCodes/{code}                                   ← join-code → institution id (public lookup, no other data)

users/{uid}/memberships/{instId}                          ← per-user index: which institutions I belong to + my role
                                                              (denormalized name/type/currency for the switcher UI)
```

This supersedes the v1 model (`users/{uid}/institutions/{instId}/...` with a flat
`users/{uid}/entries` mirror) — institutions are no longer nested under a single owning
user, because more than one user now needs to read and write the same institution.
`users/{uid}/memberships` replaces the old flat entry mirror: it answers "which
institutions am I in, and with what role" cheaply, without a collection-group query.

### Institution

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `name` | string | `""` | display name |
| `type` | string | `"Temple"` | one of `Temple`, `Church`, `Library`, `Other` |
| `currency` | string | `"INR"` | ISO-ish code, see §4 |
| `code` | string | — | 6-character uppercase alphanumeric join code, generated at creation (§2) |
| `ownerId` | string | — | uid of the creator; exactly one owner per institution (§3) |
| `createdAt` | timestamp | now | creation time (UTC) |

### Member (`institutions/{instId}/members/{uid}`)

| Field | Type | Notes |
| --- | --- | --- |
| `role` | string | `"owner"` \| `"admin"` \| `"member"` — see §3 |
| `displayName` | string | denormalized from the auth profile, for the member-list UI |
| `email` | string | denormalized |
| `photoUrl` | string | denormalized, may be empty |
| `joinedAt` | timestamp | UTC |

### Category (`institutions/{instId}/categories/{categoryId}`)

| Field | Type | Notes |
| --- | --- | --- |
| `name` | string | display name, unique per institution (case-insensitive) |
| `kind` | string | `"income"` \| `"expense"` — every entry filed under this category must carry a matching `type` |
| `createdAt` | timestamp | UTC |

### Entry

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `date` | timestamp | now | when the money moved (UTC); user-editable, defaults to *today* at creation time |
| `amount` | number | `0` | always **> 0**; direction is carried by `type` |
| `type` | string | `"income"` | `"income"` or `"expense"` |
| `category` | string | `""` | must reference a category `name` of matching `kind` configured on the institution (§5) |
| `note` | string | `""` | optional |
| `createdBy` | string | — | uid of the member who wrote the entry |
| `createdAt` | timestamp | now | write time (UTC), distinct from `date` |

## 2. Institution creation, join codes, and sharing

- **Creating** an institution: the creating user writes the `institutions/{instId}` doc
  (`ownerId` = their own uid), a `members/{uid}` doc for themselves with `role: "owner"`,
  a `users/{uid}/memberships/{instId}` index doc, and reserves a **join code** by writing
  `institutionCodes/{code} → { institutionId }`. All four writes happen in one batch —
  either all succeed or none do.
- **Join code format:** 6 characters, uppercase, drawn from an unambiguous alphabet that
  excludes visually similar characters (no `0/O`, `1/I/L`) —
  `ABCDEFGHJKMNPQRSTUVWXYZ23456789`. Generated client-side; on the rare collision
  (`institutionCodes/{code}` already exists) regenerate and retry.
- **Sharing:** the code is shown as both the raw 6-character string and a **join link**:
  `https://<web-host>/join/{code}`. Opening the link on any platform resolves the code
  (public read of `institutionCodes/{code}`, which exposes nothing but the institution
  id) and prompts sign-in + join. If a native app is installed and its App Links /
  Universal Links are registered for the same host and path, the OS opens the app
  directly instead of the browser; otherwise the web page is the universal fallback,
  which also offers the Play Store / App Store listing.
- **Joining by code:** the joining user resolves the code, then writes their **own**
  `members/{theirUid}` doc with `role: "member"` (never anything higher — see §3) and
  their own `users/{uid}/memberships/{instId}` index doc. A user can join the same
  institution only once (the member doc id is their uid, so re-joining is a no-op).

## 3. Roles

Every member of an institution has exactly one role. Higher roles are supersets of lower
ones:

| Capability | member | admin | owner |
| --- | --- | --- | --- |
| View the institution's balance, entries, categories | ✅ | ✅ | ✅ |
| Add / edit / delete entries | | ✅ | ✅ |
| Create / edit / delete categories | | ✅ | ✅ |
| Run a period export | ✅ | ✅ | ✅ |
| View the member list | | ✅ | ✅ |
| Promote a member to admin, demote an admin to member, remove a member | | | ✅ |
| Rename the institution, change its currency/type, delete the institution | | | ✅ |

- **Default role on join is always `member`.** There is no self-service path to a higher
  role — an owner must explicitly promote.
- **Exactly one owner** per institution: the creator. Ownership is not currently
  transferable (out of scope for this pass); if the owner leaves, the institution is
  orphaned for role-management purposes (existing admins keep their entry/category
  rights, but no one can promote further members) — acceptable for the current scale.
- A user's **effective role for the currently open institution** is read from
  `institutions/{instId}/members/{myUid}.role`; the UI must gate every mutating action
  (add/edit entry, category CRUD, member management) behind the role check, in addition
  to the server-side Firestore rule (§ shared/firebase/firestore.rules) — the client
  check is for UX (hide buttons a user can't use), the rule is the actual enforcement.

## 4. Currency

`CURRENCIES` is a fixed ordered list (INR, USD, EUR, GBP, JPY, AUD, CAD, CHF, CNY, HKD,
NZD, SEK, KRW, SGD, MXN, KES, UNT). Symbols: INR `₹`, USD/AUD/CAD/HKD/NZD/SGD/MXN `$`,
EUR `€`, GBP `£`, JPY/CNY `¥`, CHF `₣`, SEK `kr`, KRW `₩`, KES `KSh`, UNT `#`. Unknown
code → symbol `"$"`. (Identical list to the rest of the family.)

- **format** `(amount, code) → "<symbol> <number>"`: integer when the amount is whole,
  otherwise two decimals; the numeric sign is kept as-is. e.g. `649 INR → "₹ 649"`,
  `9.99 USD → "$ 9.99"`, `-500 INR → "₹ -500"`.
- **compact** `amount → string` using Indian-influenced suffixes: `k` (≥1 000), `l` lakh
  (≥100 000), `m` (≥1 000 000). One decimal, trailing `.0` trimmed; sign kept. `0 → "0"`.
  e.g. `1500 → "1.5k"`, `250000 → "2.5l"`, `3000000 → "3m"`, `-1500 → "-1.5k"`.

## 5. Categories

Every institution owns its **own** category list (no more global free-text defaults). An
admin/owner creates categories, each tagged with a `kind` — `income` or `expense` — and
every entry of that `type` may only reference a category whose `kind` matches. The entry
form's category picker filters to the currently-selected income/expense toggle state.

**Seed categories**, written once when an institution is created (still editable/removable
afterward — these are a starting point, not a hard-coded list like v1):

- **income**: `Donation`, `Offering`, `Membership`, `Grant`, `Other`
- **expense**: `Salary`, `Utilities`, `Maintenance`, `Supplies`, `Event`, `Other`

`categoriesFor(type)` (unchanged pure helper) still returns this **seed** list for the
given type (`income`/`expense`); any other input returns an empty list. It is used only
at institution-creation time to populate the initial category set — day-to-day, the
category picker reads the institution's live `categories` subcollection, not this list.

## 6. Summary / "this month"

`summary(entries, now)` reduces a list of entries (for the currently selected
institution) to the hero totals. **All month math is in UTC** so the three platforms
agree regardless of device timezone.

- `totalIncome` = Σ `amount` where `type == "income"` (all time).
- `totalExpense` = Σ `amount` where `type == "expense"` (all time).
- `balance` = `totalIncome − totalExpense`.
- `monthIncome` / `monthExpense` = the same sums but only for entries whose `date` falls in
  the **same UTC calendar month and year** as `now`.
- `monthBalance` = `monthIncome − monthExpense`.

Empty input → all six values are `0`.

## 7. Period export

`periodSummary(entries, start, endInclusive)` computes the opening balance, the totals
strictly within `[start, endInclusive]`, and the closing balance, for a user-chosen date
range (a period export). `start` and `endInclusive` are instants; comparisons are
inclusive at both ends.

- `openingBalance` = (Σ income where `date < start`) − (Σ expense where `date < start`).
- `periodIncome` = Σ `amount` where `type == "income"` and `start ≤ date ≤ endInclusive`.
- `periodExpense` = Σ `amount` where `type == "expense"` and `start ≤ date ≤ endInclusive`.
- `closingBalance` = `openingBalance + periodIncome − periodExpense`.
- `entryCount` = number of entries with `start ≤ date ≤ endInclusive` (entries entirely
  outside the window, before or after, do not appear in the exported list at all —
  "before" only contributes to `openingBalance`, "after" is excluded entirely).

Empty input → all four numeric values `0`, `entryCount` `0`.

The export UI presents `openingBalance`, the in-range entry list (sorted by `date`),
`periodIncome`/`periodExpense`, and `closingBalance`, and offers a share/export action
(plain-text summary, platform share sheet — no library dependency, matching the
family's "no heavy deps for a simple share" ethos).

## Keeping platforms in sync

When you change any rule here, update **all three** implementations and the
[`golden-vectors.json`](golden-vectors.json) cases in the same change. The web suite asserts
the vectors (`npm test` in `web/`); Android (`testDebugUnitTest`) and iOS (XCTest) load the
same JSON so drift fails CI on every platform.
