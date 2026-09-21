# DemoAxiom

App Android (Jetpack Compose, Material 3) demo cách **dùng SDK [Axiom](axiom/README.md)** — gọi API →
lưu local làm SSOT (Room, JSON) → chạy nền bằng WorkManager → báo trạng thái lên UI.

Dữ liệu lấy từ [dummyjson.com](https://dummyjson.com) (public, không cần key) nên clone về là chạy.

```
DemoAxiom/
├── axiom/    ← SDK, copy nguyên module từ repo gốc (không sửa gì)
└── app/      ← consumer: Compose + Koin + Coil, 3 màn hình
```

## Import Axiom vào project mới (JitPack)

Axiom phát hành public trên [JitPack](https://jitpack.io/#kaitopunch/Axiom) dưới toạ độ
**`com.github.kaitopunch:Axiom:<tag>`** — không cần token, không cần clone repo này. Ba bước:

**1. Thêm repo JitPack** — `settings.gradle.kts` (giới hạn cho group của Axiom để các dependency khác
vẫn chỉ hỏi Google/Maven Central):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.kaitopunch") }
        }
    }
}
```

<details>
<summary>Groovy (<code>settings.gradle</code>)</summary>

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url 'https://jitpack.io'
            content { includeGroup 'com.github.kaitopunch' }
        }
    }
}
```
</details>

**2. Khai báo dependency** — `build.gradle.kts` của module dùng SDK (thường là `:app` hoặc `:data`):

```kotlin
dependencies {
    implementation("com.github.kaitopunch:Axiom:1.0.0")
}
```

Retrofit, OkHttp, Gson, WorkManager, coroutines, `paging-common`, `koin-core` đi kèm dưới dạng `api`
— **không** khai lại. Chỉ tự thêm `paging-compose`/`paging-runtime` và `koin-android` nếu màn hình
dùng (như `app/` ở đây).

**3. Khởi tạo trong `Application.onCreate`**, trước `startKoin`:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Axiom.init(this) {
            isDebug = BuildConfig.DEBUG
            client("api") { baseUrl = BuildConfig.BASE_URL }
            tasks(products, users)          // TaskSpec — xem axiom/README.md §2
        }
        startKoin { modules(axiomModule, appModule) }   // nếu dùng Koin
        Axiom.get().syncAll()
    }
}
```

Viết Retrofit interface, Record và `task<T>()` theo 5 bước ở [axiom/README.md §2](axiom/README.md#2-bắt-đầu-nhanh--5-bước);
`app/` trong repo này là một consumer hoàn chỉnh để đối chiếu.

Cần biết:

- **Yêu cầu:** `minSdk ≥ 28`, Kotlin 2.x, JVM 17. AAR build với AGP 8.9.1 / Kotlin 2.1.10 / compileSdk 36.
- **Version = tên tag** trên GitHub (`1.0.0`, không có `v`). Tag có sẵn và log build tại
  [jitpack.io/#kaitopunch/Axiom](https://jitpack.io/#kaitopunch/Axiom); lần đầu một version được
  resolve, JitPack build mất ~2 phút — Gradle sẽ chờ. Thử một commit chưa tag: dùng SHA làm version
  (`com.github.kaitopunch:Axiom:44bde71ff5`).
- **R8:** Axiom keep class của nó (consumer rules trong AAR). Record class của **bạn** phải tự keep vì
  Gson đọc bằng reflection: `-keep class com.example.data.record.** { *; }` (xem `app/proguard-rules.pro`).
- Sources jar có trên JitPack (`Axiom-1.0.0-sources.jar`); Android Studio tải qua *Download Sources*.

Kiểm chứng: chính `app/` này build được với artifact JitPack thay cho module nguồn —
`./gradlew :app:installDebug -PAXIOM_SOURCE=jitpack` (xem [cuối trang](#chuyển-sang-artifact-đã-publish)).

## Chạy

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home   # Gradle 8.11 cần JDK 17
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest :axiom:testDebugUnitTest
```

Toolchain: AGP 8.9.1 · Kotlin 2.1.10 · KSP 2.1.10-1.0.29 · Gradle 8.11.1 · compileSdk 36 / minSdk 28 —
giống repo gốc của Axiom để `axiom/build.gradle.kts` build nguyên trạng. Compose BOM 2025.06.01,
Koin 4.1.1, Coil 2.7.

## Ba màn hình = ba nhóm tính năng

| Tab | Axiom API được show | Thử gì |
|---|---|---|
| **Products** | `data(spec)`, `state(key)`, `sync()`, `run()`, `clear()`, `transform { publish() }` | Kéo xuống (= `run()`) → snackbar hiện `SyncResult`: lần đầu **Success**, kéo lại trong 2 phút → **Skipped** (cửa sổ `staleAfter`), qua 2 phút mà server không đổi → **Unchanged** (SHA-256). "Clear + Sync" là lối duy nhất vượt cửa sổ. Icon ☁ mỗi dòng: ảnh còn ở URL remote hay đã cache local — chuyển từ remote sang local là `publish()` đang làm việc |
| **Users** | `asPagingData()` (`com.axiom.paging`), `cachedIn` | 208 user trong store, Paging 3 chia trang 25. Cuộn xuống giữa rồi bấm Sync/Clear+Sync: một `Pager` xuyên suốt, không nhảy về đầu |
| **Lab** | `states()`, `syncAll/cancelAll/clearAll`, `read()`, `tasks`, `axiomModule` + `axiomApi<T>()` (`com.axiom.koin`), WorkManager, `AxiomLogger` | 6 task với 5 nút mỗi task. 3 **probe** cố tình lỗi: `probe-http-404` → `HTTP 404`, không retry; `probe-http-500` → `HTTP 500`, transient, retry 2 lần (10s, 20s) rồi `Failed`; `probe-unreachable` → `NETWORK` qua client `broken`. Card WorkManager liệt kê request tag `axiom` (kể cả periodic của `categories`). Nút `api.getCategories()` gọi Retrofit service do Koin cấp qua `axiomApi<DummyJsonApi>()` |

## Cấu hình Axiom trong demo (`DemoSettings`)

| | Giá trị | Vì sao |
|---|---|---|
| `isDebug` | **`false`** kể cả debug build | App thật truyền `BuildConfig.DEBUG`; demo để `false` để cửa sổ tươi *quan sát được* |
| `defaultStaleAfter` | 5 phút (SDK mặc định 30) | Đủ ngắn cho một buổi demo; áp cho `users` (không set riêng) |
| `products.staleAfter` | 2 phút | Khác default để thấy hai giá trị khác nhau |
| `categories.staleAfter` | `Duration.ZERO` + `periodic = 15.minutes` | Luôn fetch; có PeriodicWorkRequest |
| `PRODUCT_LIMIT` | 60 | Cold start tải 60 thumbnail thay vì 194 |

Startup chỉ `sync()` 3 task nội dung (`DemoTasks.content`); 3 probe để Lab bấm tay (hoặc `syncAll()`).

## Cấu trúc `app/`

```
com.duylt.demo.axiom
├── DemoApp.kt                 Axiom.init { demo(baseUrl) } → startKoin → sync 3 task nội dung
├── data/
│   ├── DemoClients.kt         3 client: dummyjson (API), assets (chỉ callFactory), broken (host không tồn tại)
│   ├── DemoSettings.kt        các knob ở trên
│   ├── DemoTasks.kt           6 TaskSpec (products có transform + publish + ImageCache)
│   ├── DemoAxiomConfig.kt     fun AxiomConfig.demo(baseUrl, httpLogging) — pattern ":data khai báo, :app truyền param"
│   ├── remote/                DummyJsonApi (Retrofit, suspend) + wire model
│   ├── record/                ProductRecord/ProductStore, UserRecord, CategoryRecord, ProbeRecord + mapper
│   ├── local/ImageCache.kt    cache ảnh qua callFactory("assets") — phần Axiom cố ý không làm
│   └── repository/            chỉ còn đọc: data(spec).map { … }, asPagingData()
├── di/AppModule.kt            includes(axiomModule); axiomApi<DummyJsonApi>(); repos; viewModels
├── log/AxiomLogRecorder.kt    AxiomLogger → logcat + ring buffer 200 dòng cho Lab
└── ui/  products/ users/ lab/ common/ theme/
```

Test: `app/src/test` — `AppModuleTest` (Koin `verify()` trước khi `Axiom.init`) và `DemoTasksTest`
(MockWebServer + `Axiom.create { inMemoryDatabase = true }`: Success → Unchanged, cache ảnh, ảnh lỗi giữ
URL, probe 404 không transient).

## Chuyển sang artifact đã publish

`app/` lấy SDK từ đâu do `AXIOM_SOURCE` trong `gradle.properties` quyết định (`app/build.gradle.kts`):

| `AXIOM_SOURCE` | `:app` phụ thuộc vào | Dùng khi |
|---|---|---|
| `project` (mặc định) | `project(":axiom")` — module nguồn | Sửa SDK và chạy ngay |
| `jitpack` | `com.github.kaitopunch:Axiom:<AXIOM_VERSION>` từ jitpack.io | Kiểm chứng đúng artifact mà consumer ngoài nhận được |

```bash
./gradlew :app:installDebug -PAXIOM_SOURCE=jitpack          # một lần
./gradlew :app:dependencies --configuration debugRuntimeClasspath -PAXIOM_SOURCE=jitpack | grep Axiom
```

Hoặc đổi hẳn `AXIOM_SOURCE=jitpack` trong `gradle.properties`. Module `:axiom` vẫn được `include`
trong cả hai chế độ vì JitPack build release từ nó; code app không đổi dòng nào. Version resolve là
`AXIOM_VERSION` — sau khi bump mà chưa tag, chế độ `jitpack` sẽ fail (đúng: version đó chưa tồn tại);
thử commit chưa tag bằng `-PAXIOM_VERSION=<sha>`.
