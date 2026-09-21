# Axiom

SDK Android cho bài toán **gọi API → lưu local làm nguồn sự thật (SSOT) → chạy nền bằng WorkManager
→ báo trạng thái lên UI**. Bạn khai báo bằng DSL Kotlin; Axiom lo Retrofit, Room, Worker, retry.

```
client("catalogue") ──► task<T>("skins") { fetch { … } transform { … } } ──► store (Room, JSON)
                                  │                                                │
                          WorkManager chạy,                          axiom.data(spec) : Flow<T?>
                          retry, constraint                          axiom.state(key) : Flow<SyncState>
```

Một module `:axiom` (artifact `com.axiom:axiom`), ba package:

| Package | Dùng khi |
|---|---|
| `com.axiom` — `Axiom`, `AxiomConfig`, `task<T>()`, `SyncState`, `AxiomError`, `ApiEnvelope` | Luôn |
| `com.axiom.paging` — `Flow<List<T>>.asPagingData()` | Màn hình dùng Paging 3 |
| `com.axiom.koin` — `axiomModule`, `axiomApi<T>()` | Project dùng Koin |

Axiom **không** làm: SQL query trên dữ liệu server (store là JSON, lọc/sắp xếp trong bộ nhớ), auth
flow, HTTP cache, UI.

## Mục lục

1. [Cài đặt](#1-cài-đặt)
2. [Bắt đầu nhanh — 5 bước](#2-bắt-đầu-nhanh--5-bước)
3. [Đưa lên UI: trạng thái, phân trang, Koin](#3-đưa-lên-ui)
4. [Axiom chạy như thế nào](#4-axiom-chạy-như-thế-nào)
5. [Viết test](#5-viết-test)
6. [Thêm một bảng mới](#6-thêm-một-bảng-mới)
7. [Lỗi thường gặp](#7-lỗi-thường-gặp)
8. [Publish](#8-publish)
9. [API reference](#9-api-reference)
10. [Quyết định thiết kế](#10-quyết-định-thiết-kế)

---

## 1. Cài đặt

**Cùng repo (project này):**

```kotlin
// settings.gradle.kts
include(":axiom")

// build.gradle.kts của module
implementation(project(":axiom"))
```

**Từ JitPack (project khác) — public, không cần token:**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement.repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

// build.gradle.kts của module
implementation("com.github.kaitopunch:Axiom:1.0.0")
```

Toạ độ do JitPack đặt theo repo GitHub (`com.github.<owner>:<repo>:<tag>` — repo chỉ có một artifact
nên JitPack phát hành nó làm root artifact), không phải `com.axiom` như khi build local. Version = tên
tag; danh sách tag và log build tại [jitpack.io/#kaitopunch/Axiom](https://jitpack.io/#kaitopunch/Axiom).

Cần biết:

- Retrofit, OkHttp, Gson, WorkManager, coroutines, `paging-common`, `koin-core` đi kèm (`api`) —
  **không** khai lại. Vẫn tự thêm `paging-runtime` / `koin-android` nếu màn hình dùng.
- `minSdk 28`, JVM 17. Build từ terminal cần **JDK 17**
  (`JAVA_HOME=~/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home`).
- R8: Axiom đã keep class của nó. **Record class của bạn** (Gson đọc bằng reflection) phải tự keep:
  `-keep class com.example.data.catalogue.model.** { *; }` hoặc `@Keep`.

---

## 2. Bắt đầu nhanh — 5 bước

### Bước 1 — Retrofit interface

Viết như bình thường, chỉ khác là hàm `suspend`:

```kotlin
internal interface CatalogueApi {
    @GET("api/v6/items?category=skins")
    suspend fun getSkins(): ApiEnvelope<List<SkinResponse>>

    @GET("api/v6/skin-types")
    suspend fun getSkinTypes(): ApiEnvelope<List<SkinTypeResponse>>
}
```

`ApiEnvelope<T>` (tuỳ chọn) dành cho gateway trả `{ "status", "message", "data" }`:

```kotlin
envelope.requireItems("skins")   // List<T>; status 200 + data null → emptyList()
envelope.requireData("skins")    // T;       status ≠ 200 hoặc data null → ApiEnvelopeException
```

HTTP 200 nhưng `status` trong body là 500 → `AxiomError.Kind.SERVER`, **không** phải một list rỗng
được lưu như sự thật. API không có envelope thì `fetch` trả thẳng kiểu Retrofit trả về.

### Bước 2 — Record (kiểu sẽ lưu)

Store giữ **một giá trị JSON cho mỗi task key**. `T` là bất kỳ class nào Gson đọc/ghi được:

```kotlin
internal data class SkinRecord(
    val id: Int,
    val idSkinType: Int,
    val name: String,
    val thumb: String,
    val thumbDownloaded: String? = null,   // đường dẫn ảnh đã cache, transform điền vào
)

// Hai bảng phụ thuộc nhau → gom một giá trị để chúng "hạ cánh" cùng lúc
internal data class SkinStore(
    val types: List<SkinTypeRecord> = emptyList(),
    val skins: List<SkinRecord> = emptyList(),
)
```

- Tách Record khỏi Response model: đổi API không kéo theo đổi store, và ngược lại.
- Generic an toàn: `task<List<SkinRecord>>` đọc ra đúng `List<SkinRecord>`, không phải `LinkedTreeMap`.
- Đổi tên field → payload cũ đọc thành rỗng cho tới lần sync kế. Cần tương thích thì
  `@SerializedName("tên-cũ")`.

### Bước 3 — Task

```kotlin
import com.axiom.task.task
import com.axiom.task.api
import com.axiom.envelope.requireItems

val skins: TaskSpec<SkinStore> = task("skins") {
    fetch {
        val api = api<CatalogueApi>("catalogue")
        SkinStore(
            types = api.getSkinTypes().requireItems("skin types").map { it.toRecord() },
            skins = api.getSkins().requireItems("skins").map { it.toRecord() },
        )
    }
    transform { store ->                                              // tuỳ chọn
        publish(store)                                                // UI vẽ ngay bằng URL remote…
        store.copy(skins = store.skins.withCachedImages(callFactory("assets")))   // …rồi thay bằng ảnh local
    }
    staleAfter = 1.hours   // tuỳ chọn — xem §4.1
    periodic = 6.hours     // tuỳ chọn — ≥ 15 phút
}
```

| Tuỳ chọn | Mặc định | Ý nghĩa |
|---|---|---|
| `fetch { }` | **bắt buộc** | Gọi API, trả `T`. Exception → phân loại thành `AxiomError` |
| `transform { }` | không | Chạy sau `fetch`, trước khi ghi store. Có `publish(value)` để ghi giá trị trung gian ngay |
| `retry` | 3 lần, backoff 10s ×2, chỉ lỗi transient | Chỉ áp dụng khi chạy qua WorkManager |
| `constraints` | cần mạng | `androidx.work.Constraints`, truyền thẳng vào `WorkRequest` |
| `staleAfter` | `null` → 30 phút | Store trẻ hơn → bỏ qua fetch. `Duration.ZERO` = luôn fetch |
| `periodic` | `null` | Thêm `PeriodicWorkRequest`, tối thiểu 15 phút |

Trong `fetch` / `transform` bạn có `context`, `key`, `attempt`, `api<S>(client)`,
`callFactory(client)` (OkHttp thô để tải ảnh/file); riêng `transform` có thêm `publish()`. Cố ý
**không** có `Axiom` — một task không gọi được `sync` task khác.

`transform` + `publish` là pattern "hiện rows trước, tải ảnh sau": `publish(rows)` ghi store → UI vẽ
ngay; tải ảnh xong trả về bản có ảnh local → store ghi lần hai → UI thay ảnh. `state(key)` vẫn là
`Running` cho tới khi giá trị cuối được ghi. `transform` ném → `AxiomError.Kind.TRANSFORM`, không
retry, nhưng giá trị đã `publish` **vẫn còn trong store**.

Quy tắc key: không rỗng, **không chứa `:`**. Đăng ký trùng key → spec sau thay spec trước.

### Bước 4 — Khởi tạo trong `Application.onCreate`

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Axiom.init(this) {
            isDebug = BuildConfig.DEBUG                   // debug: fetch mỗi lần; release: tối đa 30 phút/lần
            client("catalogue") {
                baseUrl = BuildConfig.BASE_URL
                header("X-API-KEY", BuildConfig.API_KEY)  // header cố định → tự redact khỏi log
            }
            client("assets")                              // không baseUrl: chỉ dùng callFactory()
            tasks(skins, colors, features)
        }

        startKoin { … }                                   // SAU Axiom.init
        Axiom.get().syncAll()                             // đẩy mọi task vào WorkManager (KEEP)
    }
}
```

**Phải** nằm trong `Application.onCreate` và trước `startKoin`: WorkManager có thể dậy process chỉ để
chạy `AxiomWorker`, và worker tìm runtime qua `Axiom.get()`. Gọi `init` lần hai → trả instance cũ,
bỏ qua block (không ném).

**Pattern khuyến nghị** — `:data` khai báo, `:app` chỉ truyền tham số:

```kotlin
// :data — CatalogueConfig.kt
fun AxiomConfig.catalogue(baseUrl: String, apiKey: String) {
    client("catalogue") { this.baseUrl = baseUrl; header("X-API-KEY", apiKey) }
    client("assets")
    tasks(CatalogueTasks.DEFAULT.all)
}

// :app — GlobalApp.kt
Axiom.init(this) {
    isDebug = BuildConfig.DEBUG
    catalogue(baseUrl = BuildConfig.BASE_URL, apiKey = BuildConfig.API_KEY)
}
```

`BASE_URL` / `API_KEY` sống ở `BuildConfig` của **`:app`** (đọc từ `local.properties`) — library đọc
`BuildConfig` của chính nó thì không trỏ sang staging được.

<details>
<summary><b>Tuỳ chọn <code>AxiomConfig</code></b></summary>

| Tuỳ chọn | Mặc định | Ghi chú |
|---|---|---|
| `isDebug` | `false` | Truyền `BuildConfig.DEBUG`. `true` → mọi sync đều fetch, bỏ qua `staleAfter` |
| `defaultStaleAfter` | `30.minutes` | Cửa sổ "còn tươi" cho task không set `staleAfter`. `null` = tắt |
| `gson` | `Gson()` | Dùng cho **cả** Retrofit converter lẫn store |
| `serializer` | `GsonSerializer(gson)` | Implement `AxiomSerializer` nếu dùng kotlinx.serialization / Moshi; nhớ set `converterFactory` từng client cho khớp |
| `logger` | `AndroidAxiomLogger()` | `AxiomLogger.NONE` để tắt; hoặc wrap Timber (§4.4) |
| `clock` | `System::currentTimeMillis` | Inject cho test `staleAfter` |
| `databaseName` | `"axiom.db"` | Chỉ đổi khi hai instance Axiom cùng process |
| `inMemoryDatabase` | `false` | `true` cho test |
| `client(name) { }` | — | Khai báo client; gọi nhiều lần |
| `tasks(…)` / `task<T>(key) { }` | — | Đăng ký spec đã build / build + đăng ký một bước |

</details>

<details>
<summary><b>Tuỳ chọn <code>ClientConfig</code></b></summary>

| Tuỳ chọn | Mặc định | Ghi chú |
|---|---|---|
| `baseUrl` | `null` | Cần cho `api<S>()`; tự thêm `/` cuối. Không có → chỉ dùng được `callFactory()` |
| `connectTimeout` / `readTimeout` / `writeTimeout` | 30s | `kotlin.time.Duration` |
| `logging` | `NONE` | `BASIC` / `HEADERS` / `BODY`. Đừng `BODY` với payload lớn |
| `converterFactory` | Gson từ `AxiomConfig.gson` | Override cho riêng client này |
| `header(name, value)` | — | Header cố định, **tự redact** khỏi log |
| `headers { mapOf(…) }` | — | Header tính mỗi request (token xoay). Chạy trên thread OkHttp, phải rẻ |
| `redactHeader(name)` | — | Ẩn giá trị header khỏi log |
| `interceptor(i)` / `networkInterceptor(i)` | — | Application / network interceptor |

Mọi client là `newBuilder()` của **một** `OkHttpClient` gốc → chung connection pool. Có thể thêm/thay
client sau init bằng `axiom.client(name) { … }` (Retrofit service đã tạo trước đó vẫn giữ client cũ).

</details>

<details>
<summary><b>WorkManager & multi-process</b></summary>

Axiom **không cần `WorkerFactory`**: `AxiomWorker(Context, WorkerParameters)` là constructor mà
factory mặc định (và cả factory của Koin/Hilt) gọi được. Hai lựa chọn:

- **(a) Initializer mặc định** — không làm gì thêm.
- **(b) On-demand** (project này chọn, để WorkManager không chạy trong process phụ `:recover`):

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove" />
</provider>
```

```kotlin
class App : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.INFO).build()
}
```

Process phụ **không** chạy WorkManager → có thể `return` sớm khỏi `onCreate` trước `Axiom.init`. Nếu
process phụ *có thể* chạy worker thì phải `init` ở đó, nếu không `AxiomWorker` log
`Axiom.init was never called in this process` và trả `failure()`.

</details>

### Bước 5 — Đọc dữ liệu trong Repository

```kotlin
internal class SkinRepositoryImpl(
    private val axiom: Axiom,
    private val tasks: CatalogueTasks,
) : SkinRepository {

    // Flow<SkinStore?> — null cho tới lần ghi đầu; re-emit mỗi lần store ghi
    private val store: Flow<SkinStore> = axiom.data(tasks.skins).map { it ?: SkinStore() }

    override fun getSkins(): Flow<List<Skin>> =
        store.map { it.skins.map { row -> row.toDomain() } }

    override fun getSkinsByType(typeId: Int): Flow<List<Skin>> =
        store.map { it.skins.filter { row -> row.idSkinType == typeId }.map { it.toDomain() } }

    override suspend fun getTypeById(id: Int): SkinType? =            // đọc một lần, không observe
        axiom.read(tasks.skins)?.types?.firstOrNull { it.id == id }?.toDomain()
}
```

| Hàm | Trả về | Ghi chú |
|---|---|---|
| `axiom.data(spec)` | `Flow<T?>` | **Khuyến nghị** — kiểu suy từ `TaskSpec<T>` |
| `axiom.data<T>(key)` | `Flow<T?>` | `T` phải đúng kiểu đã đăng ký, không thì ném |
| `axiom.read(spec)` / `read<T>(key)` | `T?` (suspend) | Đọc một lần |
| `axiom.clear(key)` / `clearAll()` | suspend | Xoá payload + state; task vẫn đăng ký |

Hành vi của `data()`:

- `null` cho tới lần ghi đầu tiên (sync thành công hoặc `publish`). Repository nên map `null` → rỗng.
- Parse **một lần mỗi lần ghi**, share cho mọi collector cùng key — đổi màn hình không parse lại.
- Sync về **đúng payload đang lưu → không ghi, không emit** (so SHA-256). Mở app lại mà server chưa
  đổi gì → UI không nháy.

Join với dữ liệu local (Room của app) — store là JSON nên join trong bộ nhớ:

```kotlin
override fun getFavoriteSkins(): Flow<List<Skin>> =
    combine(favoriteDao.observeIds(), axiom.data(tasks.skins)) { ids, store ->
        val byId = store?.skins?.associateBy { it.id }.orEmpty()
        ids.mapNotNull { byId[it] }.map { it.toDomain() }   // favorite mà catalogue không còn → bỏ
    }
```

---

## 3. Đưa lên UI

### 3.1 Trạng thái sync

```kotlin
axiom.state("skins") : Flow<SyncState>               // không bao giờ complete
axiom.states()       : Flow<Map<String, SyncState>>  // mọi task, theo key
```

| `SyncState` | Ý nghĩa |
|---|---|
| `Idle` | Chưa ai gọi `sync`, hoặc bị `cancel` |
| `Scheduled` | Đã enqueue — chờ mạng hoặc chờ backoff sau lần thất bại |
| `Running(attempt)` | Đang fetch / transform / ghi |
| `Success(syncedAt)` | Lần chạy cuối đã ghi store |
| `Failed(error)` | Bỏ cuộc, không còn gì được lên lịch |

Mọi state đều mang `lastSyncedAt` (store có thể vẫn giữ dữ liệu lần trước). Shortcut: `isInFlight`,
`isSettled`.

Gom cho UI — **`Idle` phải là "đang tải"**: đó là state giữa lúc process start và lúc `onCreate` gọi
`syncAll()`; coi nó là "xong" thì màn hình nháy khung lỗi mỗi lần mở app.

```kotlin
enum class SyncStatus { IN_PROGRESS, SUCCEEDED, FAILED }

fun SyncState.toSyncStatus() = when (this) {
    is SyncState.Success -> SyncStatus.SUCCEEDED
    is SyncState.Failed -> SyncStatus.FAILED
    is SyncState.Idle, is SyncState.Scheduled, is SyncState.Running -> SyncStatus.IN_PROGRESS
}
```

**Retry:** `axiom.sync(key)` — gọi bao nhiêu lần cũng an toàn (policy `KEEP`, double-tap không xếp
chồng). Ở release, retry **không vượt** cửa sổ 30 phút (§4.1); store còn trống thì luôn fetch.

**Giữ presentation không biết Axiom** — project này cấm `presentation` import `com.axiom` (Konsist).
Cầu nối là một interface nhỏ ở `:app/util/base/sync`, ViewModel nhận nó, test đưa fake:

```kotlin
interface SyncStatusProvider {
    fun status(work: SyncWork): Flow<SyncStatus>
    fun retry(work: SyncWork)
}

class AxiomSyncStatusProvider(private val axiom: Axiom) : SyncStatusProvider {
    override fun status(work: SyncWork) = axiom.state(work.key).map { it.toSyncStatus() }.distinctUntilChanged()
    override fun retry(work: SyncWork) = axiom.sync(work.key)
}
```

### 3.2 Phân trang

```kotlin
import com.axiom.paging.asPagingData

// Repository
override fun getSkinsPaged(): Flow<PagingData<Skin>> =
    store.map { it.skins }.asPagingData().map { paging -> paging.map { it.toDomain() } }

// ViewModel
val skins: Flow<PagingData<Skin>> = repository.getSkinsPaged().cachedIn(viewModelScope)
```

`asPagingData()` giữ **một `Pager`** suốt vòng đời collect. List mới → `invalidate()` và reload quanh
anchor, y như Room; user đang ở dòng 500 không bị kéo về dòng 0 mỗi lần sync. Mặc định
`pageSize = 25`, `prefetchDistance = 5`; truyền `PagingConfig` riêng nếu cần.

### 3.3 Koin

```kotlin
import com.axiom.koin.axiomModule
import com.axiom.koin.axiomApi

val dataModule = module {
    includes(axiomModule)                                     // single<Axiom> { Axiom.get() } — lazy
    single<CatalogueTasks> { CatalogueTasks.DEFAULT }
    singleOf(::SkinRepositoryImpl) bind SkinRepository::class
    axiomApi<CatalogueApi>(client = "catalogue")              // nếu có chỗ ngoài task cần gọi API
}
```

`axiomModule` gọi `Axiom.get()` khi được hỏi lần đầu, nên `Module.verify()` chạy được trước
`onCreate`. Test cần `Axiom` thật: bind `single<Axiom> { Axiom.create(context) { … } }`.

---

## 4. Axiom chạy như thế nào

### 4.1 Lịch chạy & khi nào gọi mạng

| Gọi | Chạy ở đâu | Retry | Await |
|---|---|---|---|
| `sync(key)` / `syncAll()` | WorkManager, unique `axiom:<key>`, policy `KEEP` | theo `RetryPolicy` | không |
| `run(key)` | coroutine hiện tại (pull-to-refresh, test) | không | `SyncResult` |
| `cancel(key)` / `cancelAll()` | huỷ one-time + periodic; store giữ nguyên | | |

Trước khi fetch, mỗi lần chạy kiểm tra store còn tươi không:

| Điều kiện | Kết quả |
|---|---|
| `isDebug = true` | **luôn fetch** |
| task có `staleAfter` | fetch khi `now - lastSuccessAt ≥ staleAfter` |
| task không set | fetch khi store quá `defaultStaleAfter` (**30 phút**) |
| `defaultStaleAfter = null` | luôn fetch |

Còn tươi → `SyncResult.Skipped`, không gọi mạng, state không đổi. Mốc là lần **thành công** gần nhất
(persist, sống qua kill app); lỗi không dời mốc → task chưa từng thành công thì không bao giờ bị skip.
**Không có `force`**: cần dữ liệu mới bằng mọi giá thì `clear(key)` rồi `sync(key)`.

```kotlin
sealed interface SyncResult {
    Success(syncedAt)       // đã fetch, store đã ghi
    Unchanged(syncedAt)     // đã fetch, payload y hệt bản đang lưu → không ghi, data() im lặng
    Skipped(lastSyncedAt)   // không fetch: store còn tươi
    Failure(error)
}
```

### 4.2 Retry & lỗi

```kotlin
RetryPolicy(maxRetries = 3, initialBackoff = 10.seconds, backoffPolicy = EXPONENTIAL, retryOn = { it.isTransient })
RetryPolicy.NONE   // một lần, xong
```

| `AxiomError.Kind` | Khi nào | `code` | Retry mặc định |
|---|---|---|---|
| `NETWORK` | `IOException`: không mạng, DNS, timeout | — | có |
| `HTTP` | Retrofit `HttpException` (non-2xx) | HTTP status | 408, 429, ≥ 500 |
| `SERVER` | `ApiEnvelopeException` — envelope nói không | `status` của envelope | ≥ 500 |
| `SERIALIZATION` | Body không parse được | — | không |
| `TRANSFORM` | `transform` ném | — | không |
| `UNKNOWN` | còn lại | — | có |

Tự đặt kind: `throw AxiomException(AxiomError(Kind.SERVER, "…", code = 42))`.

Worker bị OS giết giữa chừng → `Axiom.init` reset row `running` khi process khởi động; WorkManager tự
enqueue lại. Debug WorkManager: tag `axiom`, `axiom:task:<key>` —
`adb shell dumpsys jobscheduler | grep axiom` hoặc Background Task Inspector.

### 4.3 Store

- Room riêng `axiom.db`, key → JSON chia chunk 200 000 ký tự (tránh giới hạn 2 MB của `CursorWindow`).
- **Ghi chỉ khi payload đổi**: so SHA-256 với bản đang lưu; trùng → revision đứng yên, `data()` im
  lặng, `run()` trả `Unchanged`. `publish()` cũng theo luật này.
- Đổi entity = viết migration **cùng commit**; **không có destructive fallback**.
- Serialize/parse trên `Dispatchers.Default`, Room trên IO.
- Dọn: `clear(key)` / `clearAll()`. Tắt: `close()`. `reset()` xoá singleton — chỉ cho test.

### 4.4 Logging

Mặc định `AndroidAxiomLogger()` → logcat tag `Axiom`, dòng HTTP của client `x` có prefix `[x]`.

```kotlin
logger = if (BuildConfig.DEBUG) AndroidAxiomLogger() else AxiomLogger.NONE
// hoặc wrap Timber
logger = object : AxiomLogger {
    override fun log(level: AxiomLogger.Level, throwable: Throwable?, message: () -> String) = when (level) {
        AxiomLogger.Level.DEBUG -> Timber.tag("Axiom").d(throwable, message())
        AxiomLogger.Level.INFO -> Timber.tag("Axiom").i(throwable, message())
        AxiomLogger.Level.WARN -> Timber.tag("Axiom").w(throwable, message())
        AxiomLogger.Level.ERROR -> Timber.tag("Axiom").e(throwable, message())
    }
}
```

HTTP logging là **per client** (`ClientConfig.logging`), mặc định `NONE`. Header từ `header()` tự
redact; header từ `headers { }` thì dùng `redactHeader(name)`.

---

## 5. Viết test

**Task trên JVM thuần** — fake `TaskScope`, đưa service stub, chạy `spec.fetch(scope)` rồi
`spec.transform`. Mẫu: `data/src/test/.../catalogue/TaskHarness.kt`.

```kotlin
@Test fun `publishes rows before caching images`() = runTest {
    val run = tasks.skins.runWith(FakeApiService(skins = listOf(skinResponse(id = 1))))
    assertEquals(1, run.published.size)
    assertNotNull(run.stored.skins.single().thumbDownloaded)
}
```

**Repository trên store thật** (in-memory Room, Robolectric):

```kotlin
val axiom = Axiom.create(RuntimeEnvironment.getApplication()) {
    inMemoryDatabase = true
    logger = AxiomLogger.NONE
    defaultStaleAfter = null                        // seed cùng key nhiều lần → tắt cửa sổ 30 phút
    task<SkinStore>("skins") { fetch { seed } }     // fetch trả seed thay vì gọi mạng
}
axiom.run("skins")
val repo = SkinRepositoryImpl(axiom, CatalogueTasks.DEFAULT)
assertEquals(listOf(…), repo.getSkins().first())
axiom.close()
```

`Axiom.create` **không phải singleton** — worker không thấy nó, nên dùng `run()` thay `sync()`. Mẫu:
`data/src/test/.../repository/SeededStore.kt`.

**Fetch qua wire thật** (MockWebServer):

```kotlin
val server = MockWebServer().apply { enqueue(MockResponse().setBody(json)); start() }
val axiom = Axiom.create(context) {
    inMemoryDatabase = true
    client("catalogue") { baseUrl = server.url("/").toString() }
    tasks(CatalogueTasks.DEFAULT.all)
}
assertTrue(axiom.run("skins") is SyncResult.Success)
```

**Worker** (cần singleton):

```kotlin
@Before fun setUp() { Axiom.reset(); Axiom.init(context) { inMemoryDatabase = true; … } }
@After fun tearDown() { Axiom.reset() }

val worker = TestListenableWorkerBuilder<AxiomWorker>(context)
    .setInputData(workDataOf("axiom.task" to "skins")).build()
assertEquals(ListenableWorker.Result.success(), worker.doWork())
```

**Chạy test:**

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./gradlew :axiom:testDebugUnitTest
```

---

## 6. Thêm một bảng mới

Cho project này (catalogue trong `:data`):

1. `remote/ApiService.kt` — thêm `suspend fun getX(): ApiEnvelope<List<XResponse>>`.
2. `remote/model/XResponse.kt` + `catalogue/model/XRecord.kt` + `mapper/XMapper.kt`.
3. `catalogue/Catalogue.kt` — thêm entry `X("x", imageNamespace = …)`.
4. `catalogue/CatalogueTasks.kt` — thêm `val x = task(Catalogue.X.key) { … }` và một dòng trong `byCatalogue`.
5. `repository/XRepositoryImpl.kt(axiom, tasks)` — chỉ đọc `axiom.data(tasks.x)` → map.
6. `DataModule.kt` — `singleOf(::XRepositoryImpl) bind XRepository::class`.
7. `:app/util/base/sync/SyncStatusProvider.kt` — thêm `X(Catalogue.X)` vào `enum SyncWork`.
8. Test: `CatalogueTasksTest`, `CatalogueFetchTest`, `XRepositoryImplTest`.

**Không** có Room migration, **không** có worker mới, **không** sửa `Application`.

Cho project khác: bước 1, 2, 4, 5, 6 và đăng ký task vào `Axiom.init`.

---

## 7. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Sửa |
|---|---|---|
| `Axiom.init(context) { … } has not been called` | `Axiom.get()` trước `init`, hoặc process phụ không `init` | `init` trong `Application.onCreate`, trước `startKoin` |
| Logcat `AxiomWorker ran for 'x' but Axiom.init was never called` | WorkManager dậy process mà `onCreate` return sớm | `init` trước khi return, hoặc chặn WorkManager ở process đó |
| `No Axiom client named 'x'` | Task gọi `api<…>("x")` mà không có `client("x")` | Khai báo client; tên phân biệt hoa thường |
| `client 'x' has no baseUrl` | `api()` trên client không `baseUrl` | Set `baseUrl`, hoặc dùng `callFactory("x")` |
| `Task 'x' stores List<A>, not List<B>` | `data<T>(key)` sai kiểu | Dùng `data(spec)` |
| `task key 'a:b' must not contain ':'` | Key có `:` | Đổi key |
| `data()` chỉ emit `null` | Chưa sync thành công / chưa `publish` | Xem `state(key)`; kiểm tra đã gọi `syncAll()`; logcat tag `Axiom` |
| UI nháy khung lỗi lúc mở app | Coi `Idle` là "đã xong" | `Idle` → IN_PROGRESS (§3.1) |
| Đọc ra `LinkedTreeMap` | Dùng `Class<T>` thay `Type` | Dùng `task<T>()` / `data(spec)` / `jsonType<T>()` |
| Release: record class rỗng / crash Gson | R8 strip field | Keep record class (§1) |
| Release: mở lại app không gọi API | Store còn trong cửa sổ 30 phút | Đúng thiết kế (§4.1); debug build set `isDebug = BuildConfig.DEBUG` |
| Field mới luôn `null` sau update app | Payload cũ không có field | Bình thường tới lần sync kế; cần ngay thì `clear(key)` rồi `sync(key)` |
| Test `run(key)` lần 2 nhận `Skipped` | Cửa sổ mặc định 30 phút | `defaultStaleAfter = null` trong `Axiom.create { }` |
| `WorkManager is not initialised` trong test | `Axiom.create` không có WorkManager | Dùng `run()` thay `sync()` |

---

## 8. Publish

Kênh public là **JitPack** — không có bước upload: JitPack tự clone tag của `kaitopunch/Axiom`, chạy
`install` trong `jitpack.yml` (`:axiom:publishToMavenLocal`, JDK 17) và phát hành lại kết quả dưới toạ
độ `com.github.kaitopunch:Axiom:<tag>`. Group/version trong POM bị ghi đè theo tag, nên
**tag phải trùng `AXIOM_VERSION`** (`1.0.0`, không phải `v1.0.0`).

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./gradlew :axiom:testDebugUnitTest :axiom:publishToMavenLocal   # thử consumer với ~/.m2 trước

# bump AXIOM_VERSION trong gradle.properties, commit, rồi:
git tag 1.0.0 && git push origin main 1.0.0
```

Build trên JitPack chạy **lần đầu có người resolve** (hoặc bấm *Get it* tại
[jitpack.io/#kaitopunch/Axiom](https://jitpack.io/#kaitopunch/Axiom)); mất vài phút. Log:
`https://jitpack.io/com/github/kaitopunch/Axiom/<tag>/build.log`. Build của một tag là bất
biến — tag hỏng thì sửa rồi tag version mới, hoặc đăng nhập JitPack bằng account chủ repo để xoá
build. Trước khi tag, có thể build thử bằng commit SHA làm version
(`com.github.kaitopunch:Axiom:<sha>`).

Artifact: AAR + sources jar. Fallback GitHub Packages vẫn còn (`./gradlew :axiom:publish`, credential
`gpr.user`/`gpr.key` có `write:packages` ở `~/.gradle/gradle.properties`) nhưng consumer phải có token
`read:packages` mới tải được.

---

## 9. API reference

```
src/main/java/com/axiom/
├── Axiom.kt              interface Axiom + companion init/get/getOrNull/create/reset; ext api<S>(), data<T>(), read<T>(), jsonType<T>()
├── AxiomConfig.kt        DSL của init
├── client/               ClientConfig (public), ClientRegistry (internal)
├── task/                 TaskSpec<T>, TaskBuilder<T>, task<T>(key) { }, TaskScope, TransformScope, RetryPolicy
├── state/                SyncState, SyncResult, AxiomError, AxiomException
├── envelope/             ApiEnvelope<T>, requireData(), requireItems()
├── serialization/        AxiomSerializer, GsonSerializer
├── log/                  AxiomLogger, AndroidAxiomLogger
├── store/, runtime/      (internal) Room, JsonStore, Fingerprint, TaskRunner
├── work/                 AxiomWorker (public), WorkScheduler (internal)
├── paging/               asPagingData(), DefaultListPagingConfig, ListPagingSource
└── koin/                 axiomModule, Module.axiomApi<S>()
```

```kotlin
interface Axiom {
    val tasks: List<TaskSpec<*>>
    fun task(key): TaskSpec<*>;  fun taskOrNull(key): TaskSpec<*>?;  fun register(vararg specs)
    fun client(name) { … };  fun <S> api(client, Class<S>): S;  fun retrofit(client): Retrofit;  fun callFactory(client): Call.Factory
    fun sync(key);  fun syncAll();  fun cancel(key);  fun cancelAll();  suspend fun run(key): SyncResult
    fun state(key): Flow<SyncState>;  fun states(): Flow<Map<String, SyncState>>
    fun <T> data(spec): Flow<T?>;  fun <T> data(key, type): Flow<T?>
    suspend fun <T> read(spec): T?;  suspend fun <T> read(key, type): T?
    suspend fun clear(key);  suspend fun clearAll();  fun close()

    companion: init(context) { }, get(), getOrNull(), isInitialized, create(context) { }, reset()
}
```

---

## 10. Quyết định thiết kế

- **Một module, không ba** (21/09/2026) — mọi consumer thực tế đều dùng cả Paging lẫn Koin; ba
  artifact giữ version đồng bộ là chi phí không mua được gì. Package giữ nguyên, không đổi import.
- **Store là Room riêng, key → JSON** — app không viết entity/DAO/migration cho dữ liệu server; giá
  là lọc/join trong bộ nhớ, chấp nhận vì catalogue nằm gọn trong RAM.
- **Singleton `Axiom.init`** thay vì DI vào worker — WorkManager có thể dậy process chỉ để chạy
  worker, `Application.onCreate` là hook duy nhất chạy trước. Kết quả: không `WorkerFactory`.
- **`fetch` chỉ thấy `TaskScope`** — một task không gọi được `sync` task khác.
- **Lỗi parse không retry** — payload không parse được sẽ không parse được sau 10 giây.
- **Cửa sổ 30 phút ở release là cứng, không có `force`** (21/09/2026) — một flag vượt cửa sổ có ở
  API là sẽ có chỗ gọi nó và mục tiêu "mở app lại trong 30 phút không gọi API" mất theo. Debug
  (`isDebug`) fetch mỗi lần; release chỉ còn `clear(key)` làm lối thoát có chủ ý.
- **Không destructive migration** — crash sớm còn hơn mất dữ liệu im lặng ở release.
- **So SHA-256 trước khi ghi, không lưu hash vào schema** (21/09/2026) — băm giá trị *cuối* (sau
  `transform`) để ảnh tải lỗi vẫn được thử lại lần sync sau; SHA-256 chứ không `hashCode()` 32-bit
  vì trùng hash là dữ liệu mới bị bỏ qua không log. Không thêm cột hash để khỏi migration
  `axiom.db` 1→2; giá là đọc lại payload cũ mỗi lần ghi, cỡ vài ms/MB.
