---
name: add-room-entity
description: Use this skill when adding a new Room table to the local driver-app database (`DriverDatabase`). Triggers — "add a Room entity", "create a new table for X", "store Y locally". Forces the version bump + migration + DAO + DI registration + REAL migration-execution test that this project's frozen conventions require.
---

# Skill: add-room-entity

Add a new Room entity correctly so the database compiles, migrates safely, and is exposed to repositories via Hilt.

## Background

There is exactly **one** Room database for the whole app: `DriverDatabase` in `:core:database`. As of the current session it holds only three tables — `driver_actions_outbox`, `orders_cache`, `ledger_cache` (version 1, see CLAUDE.md's "سجلّ مخطط Room"). Splitting into multiple DBs is forbidden (cross-table joins require single-DB) — same rule as POS.

## Steps

### 1. Decide the table category

| Category | Convention |
|---|---|
| **Server cache** (orders/ledger downloaded, read-mostly, e.g. `orders_cache`, `ledger_cache`) | Singular-ish table name matching the server resource. PK = server `id: Long`. Include `updated_at: Long`/`fetched_at` for freshness/expiry (this app caches trip data 7 days, breadcrumbs 30 days per CLAUDE.md's retention table — bake the expiry field in from day one). |
| **Outbox / offline writes** (`driver_actions_outbox` — accept/decline/picked-up/delivered/issue/breadcrumb actions taken offline or mid-retry) | Suffix `_outbox`. PK = a stable client-generated id **that doubles as the `Idempotency-Key`** sent to the server (see `add-api-endpoint` step 4) — this is the one deliberate difference from POS's `orders_local` pattern: POS's local order PK and idempotency key are separate concerns; here the trip-command outbox row's id IS the key, because a lost outbox row and a lost idempotency key are the same failure mode. Track `status`, `attempts`, `last_error`, `created_at`. |
| **Sync metadata** | Single-row tables keyed by resource name, same as POS. |

### 2. Create the entity

`core/database/src/main/java/app/qrmenu/driver/database/entity/<Name>Entity.kt`:

```kotlin
@Entity(
    tableName = "snake_case_plural",
    indices = [Index("foreign_key_col"), Index(value = ["unique_col"], unique = true)],
)
data class <Name>Entity(
    @PrimaryKey val id: Long,
    val name_ar: String?,      // ⚠️ nullable — a company/branch without an Arabic name
                                // crashed JSON parsing in the POS app; do not repeat it here.
    val name_en: String,
    val updated_at: Long,
)
```

Snake_case column names (matches Room's default, grep-parity with raw SQL).

### 3. Create the DAO

`core/database/src/main/java/app/qrmenu/driver/database/dao/<Name>Dao.kt`:

```kotlin
@Dao
interface <Name>Dao {
    @Query("SELECT * FROM <table> WHERE …")
    fun observe(...): Flow<List<<Name>Entity>>          // reads → Flow

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<<Name>Entity>)    // writes → suspend

    @Query("DELETE FROM <table> WHERE created_at < :cutoffMillis")
    suspend fun purgeOlderThan(cutoffMillis: Long)       // retention sweep — see CLAUDE.md's data-retention table
}
```

Rules:

- Reads return `Flow` (UI subscribes via `collectAsStateWithLifecycle`).
- Writes are `suspend`. Never block.
- **If this table holds data with a retention limit** (breadcrumbs: 30 days; trip cache: 7 days; customer phone/address: purged immediately after delivery — CLAUDE.md's "🗃️ الاحتفاظ بالبيانات" table), the DAO must expose a purge query and the caller (a `WorkManager` job, mirroring POS's `PurgeOldBreadcrumbs`-equivalent) must actually be wired — don't leave the purge query unused.

### 4. Register in `DriverDatabase`

```kotlin
@Database(
    version = 2,    // BUMP from current (see §سجلّ مخطط Room in CLAUDE.md)
    exportSchema = true,
    entities = [
        DriverActionOutboxEntity::class,
        OrdersCacheEntity::class,
        LedgerCacheEntity::class,
        <Name>Entity::class,    // ← add
    ],
)
abstract class DriverDatabase : RoomDatabase() {
    abstract fun outboxDao(): DriverActionOutboxDao
    abstract fun ordersCacheDao(): OrdersCacheDao
    abstract fun ledgerCacheDao(): LedgerCacheDao
    abstract fun <name>Dao(): <Name>Dao    // ← add
}
```

### 5. Write a migration

```kotlin
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS <table> (
              id INTEGER NOT NULL PRIMARY KEY,
              name_ar TEXT,
              name_en TEXT NOT NULL,
              updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_<table>_… ON <table>(…)")
    }
}
```

Wire it: `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`.

**Never use `fallbackToDestructiveMigration()`.** A driver's outbox row is unsent money-affecting state (a `delivered` command not yet acknowledged by the server) — destroying it on migration failure is a worse outcome here than in POS's catalog cache.

### 6. Provide the DAO via Hilt

```kotlin
@Provides fun <name>Dao(db: DriverDatabase) = db.<name>Dao()
```

### 7. 🔴 Prove the migration actually runs — reading the code is not verification

This is called out **twice, independently, in driver CLAUDE.md** (once in the verification protocol, once in the pitfalls list) and is inherited from a documented precedent in the POS repo (a 20→21 migration that looked correct by inspection): **a migration that reads correctly is not proof it runs correctly.** Write a `MigrationTestHelper`-based test that:

1. Creates a real SQLite database at the **previous** schema version (from `core/database/schemas/<n>.json`).
2. Runs `MIGRATION_<n>_<n+1>` against it.
3. Opens the result with Room and asserts it matches the exported `<n+1>.json` schema.

Column **order** differences from an `ALTER TABLE ... ADD COLUMN` append are fine (Room doesn't compare order) — everything else must match exactly.

### 8. Update CLAUDE.md (BLOCKING)

"سجلّ مخطط Room" table — append a row:

```
| 2 | 2026-XX-XX | Added <table> for <reason> | MIGRATION_1_2 in DatabaseModule.kt, verified by running it against a real v1 DB | <what you tested> |
```

## Verification

```bash
./gradlew :core:database:assembleMeniuraDebug      # Room compiler runs here
./gradlew :core:database:testMeniuraDebugUnitTest  # migration-execution test — must exist, must pass
```

Schema JSON drops under `core/database/schemas/<version>.json` — **commit it**.

## Anti-patterns

- ❌ Skipping the migration ("it's just dev data").
- ❌ `fallbackToDestructiveMigration()` — never; this app's outbox can hold unsent financial state.
- ❌ Claiming a migration is verified because it "reads correctly" — it must be run against a real prior-version database in a test.
- ❌ camelCase column names.
- ❌ Returning suspend lists for UI reads — use `Flow`.
- ❌ Adding a retention-limited table (breadcrumbs, cached PII) without a purge query and a wired sweep job.
- ❌ Non-nullable `name_ar`-style fields.
- ❌ Forgetting to update CLAUDE.md's Room schema-history table.
