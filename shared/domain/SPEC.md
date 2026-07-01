# Muthal — Canonical Domain Spec

This is the **single source of truth** for Muthal's cross-platform behaviour. The Android
(Kotlin), iOS (Swift), and Web (TypeScript) clients each re-implement this logic natively;
they must all agree, because they read and write the **same** Firestore documents in
Muthal's dedicated Firebase project. The machine-checkable cases live in
[`golden-vectors.json`](golden-vectors.json) — every platform runs them.

Muthal is a *minimal income & expense ledger for institutions* (temples, churches,
libraries, …). One signed-in user can keep **several institutions**, each with its own
ledger of income/expense entries.

| Concern | Web | Android | iOS |
| --- | --- | --- | --- |
| Institution model | `web/lib/types.ts` | `app/.../model/Institution.kt` | `Muthal/Models/Institution.swift` |
| Entry model | `web/lib/types.ts` | `app/.../model/Entry.kt` | `Muthal/Models/Entry.swift` |
| Currency | `web/lib/currency.ts` | `app/.../util/CurrencyHelper.kt` | `Muthal/Models/Currency.swift` |
| Summary / "this month" | `web/lib/summary.ts` | `app/.../util/SummaryHelper.kt` | `Muthal/Models/Summary.swift` |
| Categories | `web/lib/categories.ts` | `app/.../util/Categories.kt` | `Muthal/Models/Categories.swift` |
| Persistence | `web/lib/firestore.ts` | `app/.../util/EntryRepository.kt` | `Muthal/Services/FirestoreService.swift` |

## 1. Data model

```
users/{uid}/institutions/{instId}                       ← institution document
users/{uid}/institutions/{instId}/entries/{entryId}     ← entry (AUTHORITATIVE)
users/{uid}/entries/{entryId}                           ← flat mirror (fast "all entries" reads)
```

**Institution** fields — names/defaults must match on every platform:

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `name` | string | `""` | display name of the institution |
| `type` | string | `"Temple"` | one of `Temple`, `Church`, `Library`, `Other` |
| `currency` | string | `"INR"` | ISO-ish code, see §3 |
| `createdAt` | timestamp | now | creation time (UTC) |

**Entry** fields:

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `date` | timestamp | now | when the money moved (UTC) |
| `amount` | number | `0` | always **> 0**; direction is carried by `type` |
| `type` | string | `"income"` | `"income"` or `"expense"` |
| `category` | string | `""` | free text, defaults from §4 |
| `note` | string | `""` | optional |
| `institutionId` | string | — | parent institution id (denormalized onto the mirror) |
| `institutionName` | string | — | denormalized onto the mirror for list display |
| `currency` | string | — | copied from the institution at write time |
| `userId` | string | — | owner uid (required on the mirror for the collection-group read) |

## 2. Entries — dual write

- Every entry is written to **both** the nested authoritative path
  (`…/institutions/{instId}/entries/{eid}`) **and** the flat mirror
  (`users/{uid}/entries/{eid}`), with the **same document id**. The mirror carries `userId`
  and `institutionId` so the all-institutions "All entries" view can read it via a
  collection-group query (see the security rules).
- **Add / edit / delete** mutate both copies atomically (a batched write). Editing never
  changes the document id; deleting removes both copies.

## 3. Currency

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

## 4. Categories

Default category suggestions per `type` (free text is allowed; these just seed the picker;
order is significant and must match across platforms):

- **income**: `Donation`, `Offering`, `Membership`, `Grant`, `Other`
- **expense**: `Salary`, `Utilities`, `Maintenance`, `Supplies`, `Event`, `Other`

`categoriesFor(type)` returns the list for the given type (`income`/`expense`); any other
input returns an empty list.

## 5. Summary / "this month"

`summary(entries, now)` reduces a list of entries to the hero totals. **All month math is in
UTC** so the three platforms agree regardless of device timezone (the same reason the rest
of the family does date math in UTC). `date` and `now` are instants.

- `totalIncome` = Σ `amount` where `type == "income"` (all time).
- `totalExpense` = Σ `amount` where `type == "expense"` (all time).
- `balance` = `totalIncome − totalExpense`.
- `monthIncome` / `monthExpense` = the same sums but only for entries whose `date` falls in
  the **same UTC calendar month and year** as `now`.
- `monthBalance` = `monthIncome − monthExpense`.

Empty input → all six values are `0`.

## Keeping platforms in sync

When you change any rule here, update **all three** implementations and the
[`golden-vectors.json`](golden-vectors.json) cases in the same change. The web suite asserts
the vectors (`npm test` in `web/`); Android (`testDebugUnitTest`) and iOS (XCTest) load the
same JSON so drift fails CI on every platform.
