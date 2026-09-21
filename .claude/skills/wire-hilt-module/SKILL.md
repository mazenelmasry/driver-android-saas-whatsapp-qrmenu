---
name: wire-hilt-module
description: Use this skill when adding a new Hilt @Module, @Provides binding, or qualifier to the driver Android project. Triggers — "provide X via Hilt", "bind interface to impl", "add a singleton for Y". Enforces this project's component scoping and qualifier conventions under the `app.qrmenu.driver` package and `DriverApplication`.
---

# Skill: wire-hilt-module

Add Hilt providers correctly so they're scoped, discoverable, and testable.

## Background

- DI framework is **Hilt only**. No service locator, no manual graph construction.
- Convention plugins (`driver.android.hilt`, `driver.android.feature` — copied from POS's `pos.android.*` and renamed) already apply the Hilt plugin and KSP. Do not re-apply.
- The Application class is **`DriverApplication`** (`@HiltAndroidApp`) — not `PosApplication`. Do not subclass it, and don't copy a reference to `PosApplication` from POS code.
- `DriverApplication` is also where the foreground location service and the FCM high-priority listener are bootstrapped (unlike POS, which has no equivalent). Anything providing the location/FCM stack lives in `SingletonComponent` from `:core:location` / `:core:notifications`, not ad-hoc in `:app`.
- WorkManager workers use `@HiltWorker` + `@AssistedInject`, wired via `HiltWorkerFactory` — same pattern as POS.

## Decision tree

### Where does my binding live?

| Lifetime | Component | Module location |
|---|---|---|
| App-wide singleton (DB, OkHttp, Retrofit, `TokenStore`, `LocaleManager`, `FcmService`, location client) | `SingletonComponent` | The owning `:core:*` module's `di/` package |
| Per-Activity (single-activity app, same as POS) | `ActivityComponent` | `:app` |
| Per-ViewModel (rare; prefer constructor inject) | `ViewModelComponent` | feature module |

In practice: nearly every provider in this project goes in `SingletonComponent` from a `:core:*` module — same as POS.

### Constructor inject vs `@Provides`?

| Type | Use |
|---|---|
| Your own class (Repository, `DispatchAckService`, etc.) | `@Inject constructor(...)` — no module needed |
| Interface → Impl binding | `@Binds` in an `@Module` |
| Third-party class you can't annotate (Retrofit, Room, OkHttp, `FusedLocationProviderClient`, Firebase Auth/Messaging instances) | `@Provides` in an `@Module` |
| Pre-built complex object | `@Provides` |

## Patterns

### Constructor injection (preferred, no module)

```kotlin
@Singleton
class OrderRepository @Inject constructor(
    private val api: OrderApi,
    private val ordersCacheDao: OrdersCacheDao,
    @IoDispatcher private val io: CoroutineDispatcher,
)
```

### Interface binding

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationBindings {
    @Binds @Singleton
    abstract fun bindLocationTracker(impl: FusedLocationTracker): LocationTracker
}
```

### Plain `@Provides` — third-party clients

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    @Provides @Singleton
    fun provideFusedLocationClient(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)
}
```

### Qualifiers — always use the existing ones

```kotlin
// Declared in :core:common/.../di/DispatchersModule.kt (copied from POS, same names)
@IoDispatcher
@DefaultDispatcher
@MainDispatcher
```

If you need a new qualifier (e.g. distinguishing the FCM-registered token store from the driver auth token store, or two location update intervals — "moving" vs "stationary" per CLAUDE.md's 5–10s/30s/60s cadence table):

```kotlin
@Qualifier annotation class MovingLocationInterval
@Qualifier annotation class StationaryLocationInterval
```

Place next to where it's used, or in `:core:common/di/` if cross-cutting.

### WorkManager workers

```kotlin
@HiltWorker
class DriverActionUploadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val outboxDao: DriverActionOutboxDao,
    private val orderRepository: OrderRepository,
) : CoroutineWorker(ctx, params) { ... }
```

`HiltWorkerFactory` in `DriverApplication.workManagerConfiguration` discovers `@HiltWorker` classes automatically — do not register manually.

### Foreground location service — the one binding shape unique to this app

Unlike anything in POS, this app runs a `ForegroundService` continuously while "available" (CLAUDE.md's "📍 الموقع فى الخلفية"). Inject its dependencies the normal Hilt way (`@AndroidEntryPoint` on the `Service`, constructor-injected collaborators) — do **not** reach for a manually-constructed singleton or a static holder to get the location repository into the service; that defeats testability and is exactly the kind of shortcut the location code cannot afford given how safety-critical this component is.

### ViewModel

```kotlin
@HiltViewModel
class AvailabilityViewModel @Inject constructor(
    private val availabilityRepository: AvailabilityRepository,
) : ViewModel()
```

Composable side: `val vm: AvailabilityViewModel = hiltViewModel()`.

## Rules of thumb

- Prefer **constructor injection** over `@Provides`.
- Prefer **`@Binds`** over `@Provides` for interface→impl.
- Scope **defaults to unscoped**. Add `@Singleton` only for heavyweight/stateful types (DB, network client, location client, token store, FCM registration state).
- Cross-module visibility: a `@Provides` in `:core:database` is visible to any module depending on `:core:database`. No re-providing.
- Tests: replace bindings via `@TestInstallIn(replaces = [RealModule::class], components = [SingletonComponent::class])`.

## Checklist before finishing

- [ ] The class isn't easier to constructor-inject without a module.
- [ ] If interface→impl, used `@Binds` not `@Provides`.
- [ ] Component is `SingletonComponent` unless a narrower scope is explicitly needed.
- [ ] Module lives in the owning `:core:*` package (not `:app`).
- [ ] No duplicate `@Provides` for the same return type.
- [ ] Package is `app.qrmenu.driver.*`, application class reference is `DriverApplication` — not copy-pasted from POS.

## Anti-patterns

- ❌ `@Provides` for your own class — constructor-inject instead.
- ❌ Putting all bindings in `:app` (god module, blocks parallel build).
- ❌ Using `Dispatchers.IO` directly — inject via `@IoDispatcher`.
- ❌ `runBlocking { ... }` to satisfy a synchronous binding.
- ❌ Forgetting `@InstallIn(...)`.
- ❌ Reflexive `@Singleton` on lightweight objects.
- ❌ A static/manual singleton for the foreground location service's dependencies "because it's a Service, not a ViewModel" — use `@AndroidEntryPoint` + constructor injection.
- ❌ Referencing `PosApplication` or `app.qrmenu.pos.*` anywhere in this repo.
