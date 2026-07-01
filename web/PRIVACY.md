# Muthal — Privacy Policy

_Last updated: 2026-07-02_

**Muthal** is a minimal income & expense ledger for institutions (temples, churches,
libraries, and similar organisations), part of the Hora family of apps. This policy
explains what data Muthal collects, why, and how it is handled. It applies to the Muthal
Android, iOS, and web apps.

## Who this is for

Muthal is operated by the developer (contact: **aarshps@gmail.com**). Muthal is a personal
project, not a company.

## What we collect

- **Account information (via Google Sign-In):** your name, email address, and Google
  account identifier. This is used solely to authenticate you and to keep your ledger
  private to your account.
- **Your ledger content:** the institutions you create and the income/expense entries you
  record (amount, type, category, note, date, currency). This is data you enter.
- **Basic analytics (optional/best-effort):** anonymous, aggregated usage events (e.g.
  "entry added", "screen opened") via Firebase Analytics, used to understand feature usage.
  No entry amounts, names, or document IDs are sent as analytics parameters.

We do **not** collect location, contacts, photos, or device identifiers for advertising,
and Muthal contains **no ads**.

## How your data is stored and used

- Your data is stored in **Google Cloud Firestore** (Firebase project `hora-muthal`) and
  transmitted over encrypted (HTTPS/TLS) connections.
- Data is **strictly per-user**: security rules ensure only your signed-in account can read
  or write your own `/users/{your-id}` data. No other user can access it.
- We use your data only to provide the app's functionality (store and display your ledger,
  sync it across your devices). We do **not** sell your data or share it with third parties,
  except the infrastructure providers below that process it on our behalf.

## Service providers

Muthal relies on **Google Firebase** (Authentication, Cloud Firestore, Hosting, Analytics)
to provide sign-in, storage, hosting, and analytics. Your use is also subject to
[Google's Privacy Policy](https://policies.google.com/privacy).

## Data retention and deletion

- Your data is retained while your account exists.
- You can **delete your account and all associated data** at any time from within the app
  (Settings → Delete account), which removes your institutions and entries and your
  authentication record. You may also email **aarshps@gmail.com** to request deletion.

## Children

Muthal is not directed at children under 13 and does not knowingly collect data from them.

## Changes

We may update this policy; the "Last updated" date above reflects the latest version.

## Contact

Questions or requests: **aarshps@gmail.com**.
