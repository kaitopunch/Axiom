# DemoAxiom

App Android (Jetpack Compose, Material 3) demo cách **dùng SDK [Axiom](axiom/README.md)** — gọi API →
lưu local làm SSOT (Room, JSON) → chạy nền bằng WorkManager → báo trạng thái lên UI.

Dữ liệu lấy từ [dummyjson.com](https://dummyjson.com) (public, không cần key) nên clone về là chạy.

```
DemoAxiom/
├── axiom/    ← SDK, copy nguyên module từ repo gốc (không sửa gì)
└── app/      ← consumer: Compose + Koin + Coil, 3 màn hình
```

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

Bỏ `include(":axiom")` trong `settings.gradle.kts`, thêm repo `https://jitpack.io`, và thay
`implementation(project(":axiom"))` bằng `implementation("com.github.kaitopunch:Axiom:1.0.0")` —
chi tiết [axiom/README.md §1](axiom/README.md#1-cài-đặt). Code app không đổi dòng nào.
