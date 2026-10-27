# GuessGame

Java Android app for two-player True & Position. The interface uses the existing XML view system and Material Components. It supports same-device pass-and-play, nearby-device play, and Firebase-backed online play.

## Build environment

- Package: `com.guess.order`; min SDK 23, compile SDK 34, target SDK 29.
- Gradle wrapper 7.3.3, Android Gradle Plugin 7.2.2, JDK 17. This keeps the project compatible with AndroidIDE's existing-project workflow and avoids requiring Android Studio.
- `google-services.json` belongs at `app/google-services.json`. It is intentionally ignored by Git. Never add it to documentation or commit it.
- Dependencies: Firebase Auth 19.4.0, Firebase Realtime Database 19.7.0, Firebase Core 17.5.1, AndroidX AppCompat 1.2.0, Material Components 1.2.1, ConstraintLayout 2.0.4, Google Play services Nearby 19.3.0. Nearby 19.3.0 was selected because its published AAR supports the existing min SDK; no paid service is used.

## Game and app features

- `GameRules` is the shared rules component. It validates four distinct digits from 1 to 9, scores True (matching digits) and Position (matching places), and resolves simultaneous Position 4 as a draw.
- Same-device mode remains offline and account-free.
- Nearby mode uses Nearby Connections over Google Play services. Each secret stays on its owner's phone; only readiness, guesses and scores are sent. Both phones display the Nearby authentication digits for users to compare before accepting. Bluetooth and Wi-Fi must be enabled. Runtime Bluetooth, location, and nearby-device permissions are requested according to Android version. Nearby requires Google Play services on both devices.
- Online mode requires Firebase email/password sign-in and a unique username. It supports public/private six-character room codes, a bounded public room list, Quick Match, friends, room invitations, private per-user secrets, one guess per player each round, owner profile statistics, and recent history.
- History is kept to 20 recent records per user. RTDB listeners are removed when their screens end.

## Realtime Database schema

`database.rules.json` is a default-deny proposal that must be reviewed and tested in Firebase Emulator Suite before deployment. It is not deployed by this project.

| Path | Contents / access |
| --- | --- |
| `profiles/{uid}` | Owner-only username, display name, counters and created time. `uid` is the Firebase Auth UID. |
| `usernames/{normalizedUsername}` | Authenticated exact username lookup; maps to UID, never email. |
| `publicUsers/{uid}` | Username and display name only, for exact lookup/friends. |
| `roomListings/{code}` | Safe host display name, public/private flag, waiting/setup state and occupancy; public browsing requires an indexed `browseKey == waiting_public` query capped at 30. |
| `rooms/{code}/meta` | Room lifecycle state, round and result metadata. |
| `rooms/{code}/players/{uid}` | Room membership and display name. |
| `rooms/{code}/secrets/{uid}` | Setup secret readable only by its owner. The rules never grant access to the secrets parent. |
| `rooms/{code}/rounds/{round}/guesses/{uid}` | Immutable single guess for that round, visible to room members. |
| `rooms/{code}/rounds/{round}/scores/{uid}` | Score for that UID's guess. Only that UID can read it; the opponent writes it based on their own private secret. |
| `matchmaking/lock`, `matchQueue/{uid}` | Short client-owned matchmaking lease and small queue entries. Queue reads are bounded to indexed query of at most 40. |
| `friendRequests/{recipientUid}/{senderUid}`, `friends/{uid}/{friendUid}` | Request and reciprocal relationship records scoped to participants. |
| `roomInvites/{uid}/{code}` | Invitation visible to its recipient. |
| `matchHistory/{uid}/{matchId}` | Owner-only compact history, capped by client cleanup to the latest 20. |

RTDB rules cannot filter children from a parent read. Therefore clients browse only `roomListings`, never `rooms`. The database has no trusted server in this design: clients can falsify their own statistics and can lie about a score they submit for the opponent's guess. A modified client can manipulate game results. These rules reduce accidental exposure and unauthorized writes but do not provide anti-cheat or trustworthy competitive rankings. Quick Match's short database lease is best-effort coordination, not authoritative matchmaking; client clocks, app termination and malicious clients can still cause stale or unfair matches.

## Firebase Console setup and safe migration

1. Use the existing Firebase project and Android app registration. Enable **Authentication → Sign-in method → Email/Password** manually.
2. Confirm the existing Realtime Database is enabled and the app's Firebase project/database URL match `google-services.json`.
3. Back up/export the live database before changing rules or data. Do not remove legacy rooms. Old rooms may have both secrets in a shared-readable path, so do not migrate those secrets into the new room paths. Let active legacy games finish, then archive/remove old data manually only after confirming it is inactive.
4. Validate `database.rules.json` with Firebase Emulator Suite against the app's operations, including denied reads of another player's secret and denied second guesses. This repository does not deploy rules and has not run emulator rule tests. Deploy reviewed rules only when ready to switch all clients; the new app paths are incompatible with legacy clients.
5. Check console usage and user impact after deployment. No Cloud Functions, Cloud Run, Firestore, or paid APIs are used. Do not enable Blaze for this design.

The client creates or repairs the signed-in user's profile without resetting existing counters. Username claim is a transaction against `usernames/{name}`; usernames are lowercase normalized, 3–20 alphanumeric/underscore characters. Existing accounts without usernames must claim one before online features become available. Exact username lookup does not scan all users. Public user records never include email.

## AndroidIDE build and verification

Open this existing project in AndroidIDE, install/use JDK 17 and Android SDK 34, and keep `app/google-services.json` in place. No Android Studio setup is required. On Windows PowerShell:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

On AndroidIDE/Linux terminal use `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug`. Firebase project configuration and network access to Maven repositories are needed. The app has been compiled and the debug APK assembled in the current workspace with JDK 17/SDK 34; game logic unit tests are included. The Firebase rules, online races, and Nearby interactions still need Emulator Suite/two-device testing before public deployment.

## Firebase Spark capacity

The current Firebase documentation publishes Realtime Database Spark limits of 1 GB stored data, 10 GB monthly downloads and 100 simultaneous connections. If a Spark RTDB storage/download limit is reached, database service is disabled for the rest of that month; new connections can be rejected at the connection cap. Firebase Authentication has separate current provider/usage limits and abuse protections. These are finite shared project limits, not an unlimited-user promise. Monitor Firebase Console usage and keep public browsing, queue queries, listeners and history bounded. Sources: [RTDB usage limits](https://firebase.google.com/docs/database/usage/limits), [RTDB billing](https://firebase.google.com/docs/database/usage/billing), [Spark plan behavior](https://firebase.google.com/docs/database/faq-and-troubleshooting), [Authentication limits](https://firebase.google.com/docs/auth/limits).

## Remaining verification limits

- No Firebase Console setting was inspected or changed. Email/Password enablement and rule deployment are manual.
- No Firebase Emulator Suite is configured here, so rules correctness has not yet been simulator-verified.
- Nearby requires Google Play services plus two compatible nearby devices to verify radio behavior, permissions, connection approval and replay.
- Online results/statistics are client-written and can be falsified by modified clients; reliable matchmaking, authoritative outcomes and cheating prevention require a trusted backend, which this Spark-only project intentionally does not add.
