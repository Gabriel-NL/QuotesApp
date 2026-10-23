# Random Quotes App: understanding the current code from Unity

This project is an Android quote app written in Kotlin, with Jetpack Compose for the interface and Room for local storage. At present, it has a working navigation structure, prototype screens, and database code. **The Tags and Quotes lists read from Room.** Quote creation saves the quote and selected tag links together. Home now displays the shared daily quote and database-backed search.

This guide describes the checked-in source as it currently exists. The desired behavior is recorded separately in [Recovered requirements.txt](Recovered%20requirements.txt). No app code was changed while writing this guide, and the app was not built or run for this review.


## Notifications and list ordering

The daily quote is posted as one dismissible notification per configured quote-day period, using the same time and background scheduler as the widget. Android 13+ asks for notification permission once; Settings > General settings > Notification permissions opens Android's controls afterward. A persisted delivery period prevents startup, reboot, scheduling retries, or widget refresh from recreating a dismissed notification that day. Reroll on Home explicitly posts it again; reroll on the widget does not restore a dismissed notification. A notification still visible can update silently when its quote changes. With no eligible quote, the notification is removed. Android notification permissions, channel settings, and alarm restrictions still control delivery.

Quotes and Tags have an Order by menu (Alphabetic / Letter count) and an Ascending / Descending toggle. Search relevance groups remain first; the selected ordering applies within each group. Letter count counts Unicode code points, including spaces and punctuation. These controls survive screen-state restoration and do not change database records.

## Settings and CSV backups (requirement 11)

## Notifications and list ordering

The daily quote is posted as one dismissible notification per configured quote-day period, using the same time and background scheduler as the widget. Android 13+ asks for notification permission once; Settings > General settings > Notification permissions opens Android's controls afterward. A persisted delivery period prevents startup, reboot, scheduling retries, or widget refresh from recreating a dismissed notification that day. Reroll on Home explicitly posts it again; reroll on the widget does not restore a dismissed notification. A notification still visible can update silently when its quote changes. With no eligible quote, the notification is removed. Android notification permissions, channel settings, and alarm restrictions still control delivery.

Quotes and Tags have an Order by menu (Alphabetic / Letter count) and an Ascending / Descending toggle. Search relevance groups remain first; the selected ordering applies within each group. Letter count counts Unicode code points, including spaces and punctuation. These controls survive screen-state restoration and do not change database records.

## Settings details

The collapsible **Home & widget tag filter** section saves changes immediately. Search tags, select suggestions, and tap a selected tag to remove it. Blacklist excludes quotes with any selected tag; whitelist allows quotes with any selected tag. An empty selection allows all quotes in either mode. Untagged quotes pass a blacklist and fail a nonempty whitelist. Clear all filter tags resets the selection.

The filter applies to Home's list and to the daily selection shared with every widget. A current quote that still passes the filter remains selected; a blocked quote is replaced. No matching quotes produces an empty state and disables reroll. Filter changes do not delete quotes or tags and do not affect the Quotes screen. Settings backups include `daily_tag_names` and `daily_tag_whitelist`; names are stored with length prefixes to preserve punctuation and line breaks and remain portable across database IDs. Tag renames update selected names. Deleted tags remain removable in the filter, and a whitelist with only missing names allows no quotes.

The collapsible **Home & widget tag filter** section saves changes immediately. Search tags, select suggestions, and tap a selected tag to remove it. Blacklist excludes quotes with any selected tag; whitelist allows quotes with any selected tag. An empty selection allows all quotes in either mode. Untagged quotes pass a blacklist and fail a nonempty whitelist. Clear all filter tags resets the selection.

The filter applies to Home's list and to the daily selection shared with every widget. A current quote that still passes the filter remains selected; a blocked quote is replaced. No matching quotes produces an empty state and disables reroll. Filter changes do not delete quotes or tags and do not affect the Quotes screen. Settings backups include `daily_tag_names` and `daily_tag_whitelist`; names are stored with length prefixes to preserve punctuation and line breaks and remain portable across database IDs. Tag renames update selected names. Deleted tags remain removable in the filter, and a whitelist with only missing names allows no quotes.

Changing the backup folder moves validated app backups from the previous folder's direct children. Subfolders are never traversed; file extensions alone do not establish validity. Each valid file is staged privately in the app cache, copied under an available destination name, read back and compared with the staged content, and compared again with the source before source deletion. Existing destination files are preserved. Errors retain originals; the result reports moved, skipped, and retained counts. Failed deletion can leave a verified copy in both folders. Temporary staging files are removed after each attempt. Folder transfer does not import anything or alter the working database. If the old folder cannot be listed, the folder setting is left unchanged.

Settings is now implemented through `SettingsViewModel`, `SettingsRepository`, and `BackupRepository`. Settings are stored in Room alongside business data so a mixed data/settings import can commit or roll back in one database transaction. The new schema version is 3; `MIGRATION_2_3` only adds `app_settings`, preserving the existing tables and data.

The screen provides a daily time (HH:mm), recent-history size (0 disables protection), quote/tag limits, history clearing, default reset, an Android folder picker, and CSV import/export. Schedule changes preserve the current daily quote and reschedule the next future boundary. History reductions trim oldest records. Lower character limits do not modify stored text; unchanged longer entries remain valid. Defaults are 00:00, history 5, quote limit 5000, and tag limit 100. Internal allowed ranges are history 0-10000, quote limit 1-100000, and tag limit 1-1000.

The working database always remains internal. Folder selection uses Android's Storage Access Framework and persisted URI access. Exports create a new uniquely named document; Save as is also available. No storage permission or folder access is transferred by CSV. An imported inaccessible folder is reported and is not activated; choose it again on the destination device.

For export, select all datasets for one complete CSV or select individual groups. Required quote/tag dependencies can be included explicitly. Unused tags are included with the tags dataset. The preview displays the actual groups/counts, including automatically included dependencies. A failed export is not reported as complete, and the app attempts to delete its newly created partial file.

For import, choose a CSV, select its datasets and setting keys, select Add/merge or Replace for business data, then validate/preview and confirm. History, daily state, and settings always use replacement. Keys not selected or absent stay unchanged; unknown keys are reported and ignored. Partial replacement that would delete unselected dependent data is blocked with an explanation. Dependencies are mapped by file IDs and content, never by accidentally matching destination IDs. Existing duplicate content that makes mapping ambiguous must be resolved before import.

The import plan is built before writing. It includes the resulting settings and a snapshot of the destination. Applying it rechecks that snapshot inside the transaction; if the database changed since preview, the user must preview again. Errors roll back data, associations, history, daily state, and settings together. A complete replacement backup can restore grandfathered long entries (within the internal hard limits); the preview explicitly warns that later changed/new text must obey the configured limits. Partial/new imports obey the resulting limits.

CSV files use UTF-8 and the fixed semicolon delimiter. Header:

```text
record_type;id;quote_id;tag_id;text;name;inserted_at;local_date;key;value_type;value
```

`metadata` records contain format version 1, database schema version 3, and source timezone. `dataset` records declare groups and counts, distinguishing an empty included group from an absent group. Entry types are `quote`, `tag`, `quote_tag`, `quote_history`, `daily_quote`, and `setting`. Relationships use separate rows; no JSON is embedded. Quoted fields preserve delimiters, quotes, accents, and line breaks. A stale daily date or different source timezone is recalculated for the current local period. Current file limits are 32 MB and approximately 100,000 records; oversize files are rejected rather than truncated.

The new tests cover CSV escaping/validation, numeric duplicate IDs, dependency checks, merge/remapping, settings key selection, restored limits, explicit import confirmation, invalid-file replacement, cancellation, and denied folder access. Android database tests additionally cover full restoration, transaction rollback, stale previews, preserved long entries, and time/history behavior; these require an authorized emulator/device to execute.

## Home, daily quotes, and widget

Requirements 1, 2, 3, and 6.1.1 now have an implementation. Home uses `HomeViewModel`, with plain state and actions, to display the persisted quote of the day, a Reroll action, and a debounced search list. Text search uses the same three ranked tiers as Quotes; it retains other results after the matches. Hold a search-result row to edit that quote. With no records, the daily card is greyed out and Reroll/search are disabled.

`QuotesApplication` owns the app container and starts a replayed quote flow eagerly, loading the database quotes into RAM at process startup. `DailyQuoteRepository` stores a singleton date/quote record and recent quote history in Room. Choosing a quote, updating the daily record, and trimming history happen in one transaction. `RECENT_QUOTE_LIMIT` in `model/DailyQuotePolicy.kt` configures the history size; the effective limit is clamped to `entryCount - 1`. Empty and single-quote databases are handled without an invalid random range. A reroll from Home or any widget changes the shared persisted quote. Edits and deletions are observed; deleting today's quote selects a surviving replacement.

The Room schema is now version 2. `MIGRATION_1_2` adds only the daily-quote table and its index; existing quotes, tags, relationships, and FIFO history are preserved. Room schema export is configured under `app/schemas/`.

Add **Quote of the day** from your Android launcher's Widgets picker. The widget displays the same daily quote as Home; tapping its text opens the app, and Reroll selects a new shared quote. Multiple widget instances stay in sync. A background `JobService` refreshes the widget after scheduled alarms, boot, app updates, and clock/timezone changes. The widget also has a periodic refresh fallback. It does not require keeping a permanent foreground service alive.

The day boundary is local midnight, calculated with the device timezone and daylight-saving rules. The app refreshes at startup/resume and schedules midnight updates. On Android 12+, exact background delivery depends on the app's **Alarms & reminders** access. If unavailable, it falls back to an inexact alarm, which Android may delay while sleeping. Force-stopping the app prevents background updates until it is opened again. Source: [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms).

Validation includes policy and Home ViewModel unit tests; device tests cover daily persistence, rerolls, cache limits, deletion recovery, live text edits, and preserving data during migration. The debug app and Android-test APKs compiled, and all 18 local tests passed. A separate SQLite check validated migration SQL, schema columns/indices, preserved rows, foreign keys, and integrity. The device tests were not executed because the emulator was unauthorized in ADB; live widget placement and Android runtime migration remain unverified.

## Requirement 10.3: current architecture

The implemented data flow is now:

```text
MainActivity -> AppContainer -> ViewModel factory
Navigation entry -> ViewModel -> repository interface -> Room repository -> DAO -> database
                   | state
                   v
             route -> screen
                   ^
                   | user actions
```

- `AppContainer.kt` constructs Room repositories and injects them into ViewModels. Each navigation entry owns its ViewModel, which survives Activity recreation and is cleared when that entry is removed.
- `model/Entries.kt` contains plain quote/tag data without Room annotations. `QuoteStore` and `TagStore` define storage operations independently of their Room implementation.
- `QuoteRepository` handles reading quotes/links and transactional quote creation, edits, and deletion. `TagRepository` handles tag observation, saving, and deletion. Only repositories know DAOs/entities and transactions.
- `QuoteViewModel`, `TagsViewModel`, and `QuoteEditorViewModel` own loaded data, loading/errors, save/delete progress, and completion state. Quote drafts use `SavedStateHandle` so text and selected tags can be restored after process recreation. Display-only state such as dropdown visibility, search text, and checkbox selection stays local to Compose with `rememberSaveable`.
- `ScreenRoutes.kt` and `QuoteEditorScreen.kt` collect ViewModel state with lifecycle awareness and pass state/actions to the visual screens. The autocomplete receives its tag list as an argument; it no longer opens the database.
- `QuotesScreen`, `TagsScreen`, and `CreateQuoteScreen` draw UI from plain state and callbacks. They do not import Room, DAOs, database entities, or `DatabaseProvider`. For a screen preview, supply a sample UI state and empty callbacks. To test actions, inject fake `QuoteStore`/`TagStore` implementations as in `ViewModelTest.kt`.

For example, a tag popup's Save action calls `TagsViewModel.saveTag()`. The ViewModel calls `TagStore.saveTag()`. `TagRepository` writes through Room, the updated tag flow reaches the ViewModel, and the route redraws the screen. The screen never needs to know how a tag is stored.

The 10.3 refactor did not change the database schema; the later daily-quote implementation adds the preserving version-2 migration described above. Earlier walkthrough sections below describe the original prototype; this section supersedes their architecture descriptions.

## V2 update

The app is now named **Random Quotes App**, version 2.0 (version code 2). Quotes and Tags have debounced, case-insensitive search: whole search-phrase matches first, individual-word matches next, then other entries; each tier is alphabetically ordered. Search reorders the in-memory list rather than deleting or hiding unmatched text records. Quote tag filters hide records using all included tags first, followed by exclusions.

Quotes supports Create, Edit, long-press editing, single deletion, and checkbox-only bulk deletion with confirmation. Tag filtering cycles Any / Include / Exclude. Quote editing loads text and tag IDs, saves both in one transaction, supports Reset, and confirms leaving with an unsaved draft. Deleting quotes cleans links and FIFO history. Quotes are limited to 5,000 characters; tag names to 100. Tags keeps popup creation/editing, ID-based associations, search, long-press editing, single deletion, and bulk deletion. No custom tag colors are stored.

`QuoteEditorScreen.kt` coordinates database loading and saving; `CreateQuoteScreen.kt` draws the form. `QuoteSearch.kt` holds shared search ranking and tag-match logic. The database schema remains version 1; this update does not replace existing user data.

Build a debug-signed, installable APK with `./gradlew.bat :app:exportDebugApk`. It writes `app/build/outputs/apk/named/Random Quotes App V2.apk`. The export filename follows the app version code on future builds.

This historical V2 scope covered Quotes and Tags. Home/daily quotes/widgets were subsequently implemented as described above; Settings was subsequently implemented as described above. The earlier walkthrough below documents the project as it was originally reviewed; where it describes unfinished Quotes/Tags actions, this V2 section supersedes it.

## 1. Translate the main ideas into Unity terms

These are approximate comparisons, not identical lifecycles or APIs.

| Here | Closest Unity/C# comparison | What it does in this project |
| --- | --- | --- |
| Android Studio | Unity Editor plus IDE and build tools | Edits code, builds the app, starts an emulator, and shows device logs. |
| `MainActivity` | A startup/bootstrap `MonoBehaviour` | Android creates this entry point when launching the app. |
| `onCreate()` | Roughly startup work in `Awake()` | Installs the initial interface. Android can recreate an Activity, so this can happen again. |
| `@Composable` function | A reusable UI-building function | Describes a screen or component from its current inputs. |
| `Column`, `Row`, `Box` | Vertical layout, horizontal layout, overlapping container | Arrange UI elements. |
| `Modifier` | Layout/component settings supplied in code | Adds padding, size, scrolling, alignment, and other behavior. |
| Navigation route | A screen/panel identifier | Selects which screen to display inside the same Activity. |
| Callback such as `onClick` | `Button.onClick` / `Action` | Runs code in response to an interaction. |
| `remember { mutableStateOf(...) }` | A UI-observed runtime field | Holds temporary UI state and causes relevant UI to update when changed. |
| Room entity | A C# data model mapped to a database table | Describes a stored record. It is not a Unity GameObject or ScriptableObject asset. |
| DAO | A class/interface for save-data operations | Defines reads, inserts, edits, and deletes. |
| Coroutine with `suspend` | Asynchronous work; loosely comparable to `async`/`await` | Allows database calls to suspend without blocking the UI while waiting. |
| `Log.d(...)` | `Debug.Log(...)` | Writes messages to Android Studio's Logcat. |
| Gradle | Build and dependency configuration | Compiles Kotlin, processes Room declarations, and packages the Android app. |

There is no `Update()` loop driving these screens. Compose updates the interface when observed state changes.

## 2. What happens when you launch it

The active startup path is:

```text
AndroidManifest.xml
  -> MainActivity.onCreate()
     -> setContent
        -> MyApplicationTheme
           -> rememberNavController()
           -> Scaffold
              + bottomBar: PreBuiltBottomBar(navController)
              |              -> BottomNavbar(...)
              + content: AppNavigation(navController, padding)
                             -> HomeScreen initially
                             -> Quotes / Tags / Settings when navigated
```

1. [AndroidManifest.xml](app/src/main/AndroidManifest.xml) declares `.MainActivity` as the launcher Activity. Think of this as telling Android which bootstrap to start.
2. [MainActivity.kt](app/src/main/java/com/nog/quotesapp/MainActivity.kt) calls `setContent { ... }` to supply the Compose UI tree.
3. `MyApplicationTheme` supplies shared colors and typography.
4. `rememberNavController()` creates and remembers the controller that tracks the current destination and back stack.
5. `Scaffold` supplies a layout with an area for the bottom bar and an area for screen content. Its `paddingValues` help keep content clear of its bars.
6. `PreBuiltBottomBar` builds the four navigation buttons. Their callbacks call `navController.navigate(item.route)`.
7. `AppNavigation` maps each route to a screen function. Its starting destination is `home`.

All four destinations live inside one Activity. Switching tabs here is closer to switching UI panels than loading four Unity scenes.

### Where navigation lives

[AppNavHost.kt](app/src/main/java/com/nog/quotesapp/ui/navigation/AppNavHost.kt) contains the single `AppNavigation()` host. `PreBuiltBottomBar()` in `BottomNavbar.kt` supplies the bottom-bar destinations and click behavior. `MainActivity` creates one controller and shares it between them.

[BottomNavbar.kt](app/src/main/java/com/nog/quotesapp/ui/components/BottomNavbar.kt) contains `BottomNavbar()`, which draws the supplied buttons and forwards clicks, and `PreBuiltBottomBar()`, which configures the standard buttons and their navigation actions. Screen routes belong in `AppNavigation()`. The `create_quote` destination is opened by the Quotes screen's Create callback; registering it does not add a bottom-bar button.

[Routes.kt](app/src/main/java/com/nog/quotesapp/ui/navigation/Routes.kt) defines the route strings (`home`, `quotes`, `tags`, `settings`) and `NavItem`, a small object containing a button title and route. Navigation currently uses plain `navigate()` calls without special tab-state restoration or duplicate-destination handling.

## 3. What each screen currently does

| Screen | What you see / what works | What is unfinished |
| --- | --- | --- |
| Home | A fixed quote, a reroll button, a search field, and a results placeholder. Typed search text updates locally. | The quote is hardcoded to `The cake is a lie`. Reroll is a `TODO`. Search does not search any records. |
| Quotes | Database quotes, Create, Delete Multiple, Tags, Edit, and Delete buttons. Long quote text is limited to one line with ellipsis. | Create opens `CreateQuoteScreen`. The other action callbacks remain empty. Search is fixed to an empty string. |
| Tags | Live database list, search, creation, editing, individual deletion, and bulk deletion. | Duplicate names are permitted; tags are identified by ID. |
| Settings | An empty destination. | `SettingsScreen()` has an empty body. |

### Home: state that actually changes

In [HomeScreen.kt](app/src/main/java/com/nog/quotesapp/ui/screens/HomeScreen.kt):

```kotlin
val searchText = remember { mutableStateOf("") }

OutlinedTextField(
    value = searchText.value,
    onValueChange = { searchText.value = it },
    // Other parameters omitted here.
)
```

`value` is what the field displays. `onValueChange` receives the new text, and writing it back into the observed state lets Compose update the field. `it` is Kotlin's implicit name for a single lambda argument, similar to `newText => ...` in C#.

`remember` keeps the state across **recomposition**, which means Compose rerunning relevant UI functions after an observed input changes. It is not a disk save. It does not guarantee survival when you leave the screen, Android recreates the Activity, or the process ends. There is no `rememberSaveable` or ViewModel state implementation here.

`quoteOfTheDay` is also remembered state, but nothing currently assigns a new quote to it. The private `QuoteForToday()` function only draws the card and forwards the reroll click to its caller.

One layout detail: `HomeScreen` accepts a `modifier`, but its root `Box` uses a fresh `Modifier.padding(16.dp)` instead. The incoming modifier is currently ignored.

### Quotes: callbacks prepared for future behavior

In [QuotesScreen.kt](app/src/main/java/com/nog/quotesapp/ui/screens/QuotesScreen.kt), the function accepts the list and actions as parameters:

```kotlin
onEditClick: (Int) -> Unit = { _ -> }
```

This is roughly a C# `Action<int>` with a default handler that does nothing. `Unit` is analogous to `void`, and `_` means the argument is deliberately ignored.

`QuoteCard` forwards the clicked quote's ID to the screen callback. The navigation host supplies `onCreateClick` to open `create_quote`. Edit and Delete still use their default empty handlers.

The navigation host observes `QuoteDao.observeAll()` and supplies the stored quotes. The dummy `testQuotes` list has been removed. `QuoteItem` is a UI data model, mapped from the database's `QuoteEntity`.

The search field uses `value = ""`. It can send text-change events, but no handler stores them and its displayed value remains empty. This differs from Home's state-backed field.

`LazyColumn` is a scrolling list that composes visible items as needed. `QuoteCard` draws each row with text, Edit, and Delete controls. The quote list still has no working bulk-selection or deletion behavior. Tags have their own implemented list actions and use a popup for creation and editing.

## 4. How Compose differs from a Unity UI hierarchy

In Unity you might keep references to a `Text` and a `Button`, then change their properties. Here, a composable describes the interface for a given state:

```text
Current state -> composable functions -> displayed UI
User interaction -> callback -> state change -> relevant UI updates
```

Do not assume a composable's body executes once like `Start()`. It may run repeatedly, and persistent operations such as inserting a database row should not simply be placed in that body.

Common syntax in this project:

| Kotlin | C# interpretation |
| --- | --- |
| `val` / `var` | A reference/value you cannot reassign / one you can reassign. `val` does not make the referenced object immutable. |
| `fun HomeScreen(...)` | A method declaration; Kotlin also allows functions outside classes. |
| `data class QuoteItem(...)` | A data-focused class with generated equality, copy, and string helpers; similar in purpose to a C# record. |
| `object DatabaseProvider` | A singleton declaration. |
| `String?` | A nullable type. |
| `INSTANCE ?: ...` | Use `INSTANCE` if non-null; otherwise evaluate the right side, similar to `??`. |
| `{ ... }` passed as a parameter | A lambda, similar to a delegate or `Action`. |
| `by mutableStateOf(...)` | Delegated property syntax: reads/writes go through Compose state without explicitly using `.value`. |
| `QuoteEntity::class` | A Kotlin class reference, roughly comparable to `typeof(QuoteEntity)`. |

`Modifier` chains apply layout behavior in order. `dp` means density-independent pixels for layout; `sp` is used for text and accounts for text scaling. `.weight(1f)` gives an element a share of remaining space in a row or column.

## 5. The database exists independently of the UI

Room is a layer over a local SQLite database. You declare tables and operations in Kotlin, and Room generates implementation code during the build through KSP.

```text
DatabaseProvider.getDatabase(context)
  -> AppDatabase (database name: quotes_database)
     -> quoteDao()            -> quotes
     -> tagDao()              -> tags
     -> quoteTagCrossRefDao() -> quote_tags_cross_ref
     -> fifoDao()             -> quote_fifo
```

[DatabaseProvider.kt](app/src/main/java/com/nog/quotesapp/data/db/DatabaseProvider.kt) holds an in-process singleton database instance. `context.applicationContext` gives Room the application's context, rather than tying it to one Activity instance. `synchronized` coordinates construction across threads, and `@Volatile` makes instance updates visible between threads. The database data is stored on disk; the singleton reference is only runtime state.

[AppDatabase.kt](app/src/main/java/com/nog/quotesapp/data/db/AppDatabase.kt) registers four entities and exposes their DAOs. Its schema version is `1`.

| Entity / table | Fields | Purpose and current limits |
| --- | --- | --- |
| `QuoteEntity` / `quotes` | Auto-generated `id`, `text` | Stores quote text. |
| `TagEntity` / `tags` | Auto-generated `id`, `name` | Stores tag names. |
| `QuoteTagCrossRefEntity` / `quote_tags_cross_ref` | `quoteId`, `tagId` | Links quotes to tags. The pair is the primary key, so the same pair cannot be stored twice. |
| `FifoEntity` / `quote_fifo` | Auto-generated `id`, `quoteId`, `insertedAt` | Storage foundation for quote history. No history limit, ordered queue logic, or random-selection algorithm currently uses it. |

The cross-reference table has foreign keys with cascade deletion: deleting a quote or tag also deletes its links. It does not delete the other linked quote/tag. The FIFO table has no declared foreign key, so quote deletion does not automatically remove FIFO entries.

The recovered requirements proposed a separate ID on the relationship table; the implementation instead uses the two IDs together as its primary key.

The files in [data/dao](app/src/main/java/com/nog/quotesapp/data/dao) contain SQL operations. Quote and tag DAOs insert, read, update by ID, check existence, and delete. The relationship DAO inserts/removes links and reads/clears the table. The FIFO DAO offers basic insert, update, delete, and read operations. Queries using `SELECT *` without `ORDER BY` do not promise an alphabetical or chronological result order.

`getAll()` returns a snapshot. Quote and tag DAOs also expose `observeAll()` Flows, which the list screens collect to update automatically after database changes.

### Where the intended connecting layers stand

[QuoteRepository.kt](app/src/main/java/com/nog/quotesapp/data/repository/QuoteRepository.kt) creates quotes and their tag links in one Room transaction. [QuoteViewModel.kt](app/src/main/java/com/nog/quotesapp/viewmodel/QuoteViewModel.kt) now owns the quote-list data and deletion actions. The screen state flows as follows:

```text
Screen callbacks -> ViewModel (screen state and actions)
                -> Repository (data access coordination)
                -> DAO -> Room database
```

The navigation host creates entry-scoped ViewModels through `AppContainer`. Quote and tag screens pass actions to their ViewModels; only repositories access DAOs. A successful quote save updates the editor state and its route returns to Quotes. The startup CRUD demo has been removed.

## 6. The existing database helpers are manual experiments

[DBInteractions.kt](app/src/main/java/com/nog/quotesapp/DBInteractions.kt) contains top-level helpers for adding quotes/tags, updating text/names, linking IDs, and formatting database contents as text. `addQuoteTagCrossRef` checks that both records exist before inserting a link.

It also contains `ComponentActivity.testFullDatabase()`. This is an **extension function**: Kotlin lets you call it like an Activity method without modifying the Activity class. Its coroutine is launched through `lifecycleScope`, whose work is cancelled when its lifecycle is destroyed. This is different from a Unity `IEnumerator` coroutine; `suspend` functions are not automatically background threads, and Room handles asynchronous execution of these database calls.

If manually invoked, this helper:

1. Clears relationship, tag, and quote tables. It does not clear FIFO history.
2. Inserts three game quotes and two tags.
3. Attempts to create two relationships.
4. Captures a text dump of the database.
5. Deletes the first quote.
6. Assigns the earlier dump to `debugText`.

There are two details to watch when reading this experiment:

- `addQuoteTagCrossRef` declares its positional arguments as **tag ID, then quote ID**, but the test passes **quote ID, then tag ID**. The IDs can accidentally match, produce incorrect links, or fail the existence checks.
- The displayed dump is captured **before** deletion. There is no post-deletion dump or assertion proving the final state.

`debugText` is a top-level Compose state property in `MainActivity.kt`, initially `Waiting...`. No current screen reads it, so changing it will not show a debug panel.

[DatabaseTesting.kt](app/src/main/java/com/nog/quotesapp/data/db/DatabaseTesting.kt) contains another unused experiment, `testDatabase(...)`: it inserts `Test quote`, reads all quotes, and logs them with the `ROOM_TEST` tag. Repeated manual calls insert additional rows.

## 7. Where to find things in Android Studio

The **Project** view exposes the physical folder structure used in these links. Kotlin source happens to live under `java/`; that folder name does not mean the app is written in Java.

| Location | What to look for |
| --- | --- |
| `app/src/main/java/com/nog/quotesapp/` | App source, starting with `MainActivity.kt`. |
| `ui/screens/` | Full-screen interface functions. |
| `ui/components/` | Bottom-bar rendering, standard buttons, and their navigation actions. |
| `ui/navigation/` | Route identifiers and the single active navigation host. |
| `ui/theme/` | Colors, typography, and light/dark theme selection. Android 12+ uses system dynamic colors by default. |
| `data/entity/`, `data/dao/`, `data/db/` | Database models, SQL operations, and database construction. |
| `app/src/main/res/` | Android resources such as icons, strings, and XML theme/backup settings. Compose layouts here are written in Kotlin. |
| `app/src/main/AndroidManifest.xml` | Launcher and application declarations. No widget receiver or background service is currently declared. |
| `app/src/test/` | Tests that run on the development computer; roughly Unity Edit Mode tests. |
| `app/src/androidTest/` | Tests that run on Android hardware/emulator; loosely Unity device/Play Mode tests. |
| `build/`, `app/build/`, `.gradle/` | Generated outputs and caches. Read the source above to understand the app. |

Use **Go to Declaration** on a function name to follow calls, **Find Usages** to see who calls it, and **Logcat** to inspect device logs. In particular, Find Usages explains why the database helpers have no visible effect: they have no startup caller.

## 8. Build configuration and existing test limits

[settings.gradle.kts](settings.gradle.kts) names the project and includes the `app` module. [build.gradle.kts](build.gradle.kts) declares shared build plugins. [app/build.gradle.kts](app/build.gradle.kts) configures the actual Android application and its dependencies.

The app configuration currently declares `applicationId = "com.nog.quotesapp"`, minimum SDK `24`, target SDK `35`, compile SDK `36`, and Java/Kotlin bytecode target `11`. The bytecode target is not necessarily the JDK version required to run Gradle.

[gradle/libs.versions.toml](gradle/libs.versions.toml) centralizes dependency names and versions. The `libs.*` expressions in Gradle files refer to this catalog. Compose supplies UI, Navigation Compose supplies routes, and Room plus KSP supplies database code generation.

One visible configuration issue is `roomRuntime = "..."`. `androidx-room-runtime` uses that value as its version, while Room KTX and the compiler use `room = "2.8.4"`. The literal placeholder needs resolving before relying on a successful dependency sync/build. This guide has not verified other build compatibility.

The Gradle wrapper (`gradlew.bat` and `gradle/wrapper/`) selects the project's Gradle distribution. `local.properties` supplies the machine-specific Android SDK location.

To try the app, open the project root in Android Studio, sync Gradle, select an emulator or connected device, and Run the `app` configuration. Treat the screens described above as source-based expectations until you have a successful build.

The existing tests are templates: the local test checks `2 + 2 == 4`; the instrumented test expects `com.nog.myapplication`, which differs from this app's configured ID. Neither validates quote behavior. The database helper functions are manual experiments, not automated tests.

## 9. Read the project in this order

1. `MainActivity.kt`: find the actual startup calls.
2. `Routes.kt`, `BottomNavbar.kt`, then `AppNavHost.kt`: follow a bottom-button click to its screen.
3. `HomeScreen.kt`: understand observed state and callbacks.
4. `QuotesScreen.kt`: distinguish UI structure from supplied data and action handlers.
5. `QuoteEntity.kt`, `QuoteDao.kt`, `AppDatabase.kt`, `DatabaseProvider.kt`: follow one record from its model to storage access.
6. `DBInteractions.kt`: see example database calls and the limits of the manual experiments.
7. `Recovered requirements.txt`: compare the intended features with the prototype.

The largest remaining piece is connecting UI state and callbacks to persistence. Daily selection, reroll/history behavior, quote search ranking and debounce, quote editing, quote tag filtering, quote bulk deletion, and widgets remain unfinished. Tag management is implemented.

## Tags screen implementation

`TagsScreen.kt` observes `TagDao.observeAll()`, so inserts, edits, and deletions update the alphabetical list automatically. Search filters tag names without changing the database. Selection for bulk deletion is by ID and checkbox; deletion requires confirmation and runs in a transaction.

`TagsScreen.kt` opens an `AlertDialog` for Create and Edit, with a tag-name input, Create/Save, and Cancel. Saving inserts a new tag or updates the existing ID, preserving quote links. The popup closes only after a successful save; Cancel discards its draft. Tags have no custom color settings. `CreateQuoteScreen.kt` is dedicated to quote creation and its tag autocomplete. `MainActivity` hides the bottom bar on the `create_quote` destination.

Manual check: create a tag, search for it, edit its name, and return to verify the updated name. Start an edit and press Cancel to verify the draft is discarded. Delete one tag with confirmation, then create two tags and try checkbox-based bulk deletion. Existing quotes should remain after deleting their tags. Reopen quote creation to see the current tags in autocomplete.
## Quote of the day widget

On the home screen, hold an empty area, choose Widgets, and add Random Quotes App's Quote of the day. Choose a transparent background or a solid custom RGB color, choose black or white text, and Save. Each widget remembers its own appearance. To change it later, use its Style button or the launcher's Edit/Reconfigure option.

Hold the widget and drag its resize handles. Both dimensions can change within the launcher's grid (minimum requested size: 110 x 80 dp). Below 150 dp tall, the title and action buttons disappear to leave space for the quote. Long quotes wrap and show an ellipsis if they still do not fit; tapping the quote opens the app.

The widget reads the same persisted daily quote as Home. Reroll from either place updates that shared quote and all widgets. Appearance changes and resizing do not request a reroll. Appearance uses per-widget SharedPreferences; `onRestored` remaps these preferences if Android restores widget IDs. Launcher backup/restore support determines whether widgets themselves are restored; appearance is not part of the app's CSV export.

## Saving a quote

`CreateQuoteScreen` sends draft changes and Save to `QuoteEditorViewModel`. The ViewModel calls `QuoteRepository.createQuote()`, which trims the text and inserts the quote and distinct tag links in a single transaction. A failed link insertion rolls back the quote as well. While saving, repeat submissions are disabled. Errors keep the draft open; success returns to Quotes, whose Room Flow displays the added record. No existing database rows are deleted as part of removing the sample UI data.

`QuoteCreationTest` uses an isolated in-memory database to cover tagged creation, untagged creation, and transaction rollback when a selected tag no longer exists.
