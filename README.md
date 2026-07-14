# Orderly

A personal Android app that reads shopping-order emails from Gmail
(**read-only** — it can never modify or delete your mail), stores them on-device,
and gives you one place for all online purchases.

- Detects orders from supported stores (Amazon, Daraz, Temu, AliExpress, and more)
- Tracks Pakistani couriers (PostEx, TCS, Leopards, Pakistan Post) once a tracking
  number appears — live hub updates where public tracking allows, plus email timeline
- Dashboard for active, in-transit, and delayed orders
- Permanent on-device purchase history and basic spending insights
- Everything stays on your phone. No server, no analytics, no third parties.

## One-time setup (2 minutes, no Google Cloud needed)

The app reads Gmail over IMAP using a Google **App Password** — same approach as
Expense Tracker:

1. Enable **2-Step Verification** on your Google account (if not already on):
   [myaccount.google.com/security](https://myaccount.google.com/security)
2. Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
   and create an app password named "Orderly".
3. Open the app and enter your Gmail address plus that 16-character password.

The mailbox is opened in read-only mode, so the app cannot modify or delete mail.

## Build & install

Open the project in Android Studio and press Run, or from the command line:

```bash
./gradlew :app:installDebug
```

## Adding stores

Store emails are matched by sender domain in
`app/src/main/java/com/orderly/app/data/parser/StoreRegistry.kt`.
Parsing lives in `data/parser/OrderParser.kt`.

## How it works

- **Sync**: on demand and every 6 hours via WorkManager. Connects to
  `imap.gmail.com` and searches All Mail for **order/shipping keywords**
  (any shop) plus known store/courier domains. Parses each email and upserts
  orders (keyed by store + order number when available). Unknown shops use the
  sender display name — you do **not** need to add every company domain.
- **Live tracking**: after email sync, in-transit orders with a tracking number are
  refreshed against public PostEx / Leopards (and TCS when available) track pages so
  you get “arrived at …” style timeline updates on device.
- **Retention**: orders are kept permanently on device. Sync looks back up to
  365 days of mail for new/updated orders.
- **Storage**: Room (SQLite) on-device. Credentials stay in private app storage.
