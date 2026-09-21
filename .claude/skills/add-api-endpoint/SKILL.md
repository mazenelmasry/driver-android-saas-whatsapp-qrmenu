---
name: add-api-endpoint
description: Use this skill when binding a new Laravel driver-app endpoint to the Android client. Triggers — "wire the /driver/X endpoint", "add a Retrofit call for Y", "consume the Z API". Forces the DTO + interface + Hilt provider + repository wrapping that this project's network conventions require, plus the mandatory backend-side work (the backend does not exist yet).
---

# Skill: add-api-endpoint

Bind a `/api/v1/driver/*` endpoint to a Retrofit interface, with proper DTO/domain mapping and Hilt wiring.

## ⚠️ Read this first — the backend does not exist yet

Unlike the POS app (whose backend is live), **no `/api/v1/driver/*` route, controller, model, or migration exists yet** in `C:\laragon\www\filamentv4-saas-whatsapp-qrmenu`. `openapi/driver.v1.yaml` in THIS repo is the *intended* contract, written first on purpose (see its header comment) — it is not yet backed by a running endpoint.

So "adding an endpoint" here is **two-repo work**, always in this order:

1. Backend repo: read that repo's own `CLAUDE.md`, implement the controller/route/FormRequest/Resource per the shape already specified in `openapi/driver.v1.yaml`, add to `routes/api.php` under `Route::prefix('driver')`, run its test suite, remember `route:cache` is mandatory after any new `driver/*` route on deploy.
2. This repo: DTO + Retrofit interface + Hilt provider + repository, as below.

**Never invent a response shape.** If what you need isn't already specified in `openapi/driver.v1.yaml`, update the YAML first (both repos must agree — see the file's own header), then implement, then keep the mirror (`core/network/src/test/resources/contract/driver.v1.json`) in sync in the same change.

## Background

- Base URL is set per-flavor (`meniura` → `app.meniura.com`, `taaj` → `app.taaj.me`) in `:core:network`'s `NetworkModule.kt`: `BuildConfig.API_BASE_URL + "/api/v1/driver/"`. Do not include this prefix in `@GET`/`@POST` annotations.
- Auth is a bespoke `DriverToken` Bearer — **not Sanctum, and not `PosStaffToken`**. The interceptor adds `Authorization` automatically; never set it per-call, and never point a driver call at a `/pos/*` path.
- Locale header (`ar/en/ur/bn/hi`) is added by an interceptor — don't add it per-call.
- **Every trip-command POST (`ack`, `accept`, `decline`, `claim`, `release`, `picked-up`, `delivered`, `issue`, `breadcrumbs`) MUST carry `Idempotency-Key`.** The driver is on a phone, on mobile data, in a car — retries are the normal case, not the exception.
- `occurred_at`, where the endpoint accepts it, is the **device's** clock, clamped server-side to (now − 6h, now). Send the device time; do not send server time.

## Steps

### 1. Confirm the shape in the contract, not by inventing it

Open `openapi/driver.v1.yaml` at this repo's root and find the path. If it's missing or the shape is wrong for what you need, that is itself the first deliverable: propose the YAML change to the user, then implement backend + app together — don't guess a JSON shape that later has to change in two repos.

### 2. DTOs in `:core:network`

Path: `core/network/src/main/java/app/qrmenu/driver/network/dto/`

```kotlin
@Serializable
data class DriverOrderOfferedDto(
    val id: Long,
    @SerialName("order_number") val orderNumber: String,
    val status: String,
    @SerialName("driver_fee") val driverFee: Double,
    @SerialName("cash_to_collect") val cashToCollect: Double,
    // ⚠️ name_ar/name_en style fields (company name, branch name…) MUST be
    // nullable — see driver CLAUDE.md gotcha "name_ar nullable in every DTO",
    // inherited verbatim from a real POS production crash.
)
```

Rules:

- DTOs are **API-shaped** (snake_case Laravel keys via `@SerialName`), never domain models.
- Money fields are `Double`/`Float` — the contract states money is a JSON number; a string amount is a bug worth failing loudly on, not coercing.
- Timestamps are ISO-8601 **with offset** (`Carbon::toIso8601String()`), e.g. `2026-09-21T14:15:00+03:00`. Don't assume UTC.
- `Json { ignoreUnknownKeys = true }` — but the contract test enforces the reverse (a response field NOT declared in the YAML fails), so keep the DTO honest with the YAML, not just with what you personally read.

### 3. Retrofit interface

Path: `core/network/src/main/java/app/qrmenu/driver/network/api/<Resource>Api.kt`

```kotlin
interface OrderApi {
    @GET("orders/available")
    suspend fun available(): AvailableOrdersEnvelopeDto

    @POST("orders/{id}/accept")
    suspend fun accept(
        @Path("id") id: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DriverOrderAssignedDto
}
```

Rules:

- `suspend` returns the body directly — errors surface as `HttpException`/`IOException`, mapped to `DomainResult` at the repository boundary.
- Path is relative — no leading `/api/v1/driver/`.

### 4. Idempotency-Key value

For a trip command tied to a local action (accept/decline/picked-up/delivered/…), the key must be **stable across retries of the same user action**, not a fresh UUID per call attempt. Generate it once when the intent fires (e.g. `"${orderId}-${action}-${System.currentTimeMillis() / 1000}"` is wrong — use a value persisted with the pending outbox entry, see `add-room-entity` for the outbox table shape) and reuse it on every retry of that same attempt.

### 5. Hilt provider

In `:core:network/.../di/NetworkModule.kt`:

```kotlin
@Provides @Singleton
fun orderApi(retrofit: Retrofit): OrderApi = retrofit.create(OrderApi::class.java)
```

### 6. Repository in the consuming module

Repositories live in **feature** or `:core:database` modules, not in `:core:network`:

```kotlin
class OrderRepository @Inject constructor(
    private val api: OrderApi,
    private val dao: OrdersCacheDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun accept(orderId: Long, idempotencyKey: String): DomainResult<DriverOrderAssigned> =
        withContext(io) {
            try {
                DomainResult.Success(api.accept(orderId, idempotencyKey).toDomain())
            } catch (e: HttpException) {
                DomainResult.Failure(DomainError.fromCode(e.errorCode(), e.code()))
            } catch (e: IOException) {
                DomainResult.Failure(DomainError.Network(e.message))
            }
        }
}
```

- **Error mapping goes through the frozen `ErrorCode` enum, never the message string** (driver CLAUDE.md §"أكواد الأخطاء"): the backend speaks ar/en only, the driver may be reading Urdu, Bengali, or Hindi. A code not in `DomainError.fromCode`'s known set must fall back to a generic translated message + a Sentry log — never surface `message` directly.

### 7. Update the contract mirror + CLAUDE.md (BLOCKING)

- If you changed `openapi/driver.v1.yaml`, regenerate `core/network/src/test/resources/contract/driver.v1.json` in the **same commit**.
- Add the endpoint to CLAUDE.md's "ما يتغيّر فى الباك اند" / API section if it's genuinely new surface, and note in the session handoff whether the backend side landed yet.

## Verification

```bash
./gradlew :core:network:assembleMeniuraDebug
./gradlew :core:network:testMeniuraDebugUnitTest   # contract mirror test
```

If the backend exists yet: `php artisan test --compact --filter=Driver` in the backend repo, `vendor/bin/pint app tests database routes lang config --dirty`.

## Anti-patterns

- ❌ Inventing a response shape not in `openapi/driver.v1.yaml`.
- ❌ Pointing any call at `/pos/*` — a driver token must never reach a POS route.
- ❌ Returning DTOs from repositories — only domain models leak past the network layer.
- ❌ Missing `Idempotency-Key` on any trip command.
- ❌ Branching on `error.message` instead of `error.code`.
- ❌ Assuming the backend already exists — check, and say so if it doesn't.
- ❌ Non-nullable `name_ar`/`name_en`-style fields in a DTO.
