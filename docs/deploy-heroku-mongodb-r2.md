# Triển khai: Heroku + MongoDB Atlas + Cloudflare R2

Tài liệu này mô tả cách đưa backend lên Heroku với MongoDB bên ngoài Heroku (Atlas) và ảnh trên Cloudflare R2,
cách chuyển dữ liệu PostgreSQL/ảnh cũ, cách rollback và các giới hạn còn lại.

> Các thông tin về giá/giới hạn gói dịch vụ thay đổi theo thời gian — kiểm tra lại trang chính thức của Heroku,
> MongoDB Atlas và Cloudflare trước khi chọn gói.

## 1. Kiến trúc

```
Frontend ──HTTPS──> Heroku web dyno (Spring Boot, 512 MB)
                      ├── MongoDB Atlas (replica set, transaction)   metadata + dữ liệu nghiệp vụ
                      └── Cloudflare R2 (S3 API, credentials ở backend)  ảnh: files/<yyyy-MM-dd>/<uuid>-<tên>.<ext>
Trình duyệt ──HTTPS──> R2 custom domain (https://img.example.com/files/...)   tải ảnh trực tiếp, không qua dyno
```

### Mô hình dữ liệu MongoDB
| Collection | Ghi chú |
|---|---|
| `admin_users`, `refresh_tokens` (TTL theo `expiresAt`), `customers` | `emailKey` lower-case cho unique không phân biệt hoa thường |
| `categories`, `brands`, `sizes`, `colors` | soft delete bằng `deleted` + `deletedAt` |
| `products` | ảnh gallery nhúng trong `images[]`; `htmlFilePaths[]` = ảnh dùng trong `descriptionHtml` |
| `product_variants`, `skus` | collection riêng: sửa từng cái, tham chiếu từ đơn hàng, trừ tồn kho nguyên tử |
| `orders` | `items[]` và `statusHistory[]` nhúng; tiền là Decimal128 scale 2 |
| `coupons` | `usedCount` chỉ tăng bằng `$inc` có điều kiện `usedCount < usageLimit` |
| `collections` (`products[]` nhúng), `blog_posts`, `landing_banners` | `htmlFilePaths[]` cho HTML |
| `files` | chỉ metadata: `path`, `objectKey`, `storageProvider`, `status`, kích thước, lịch sử dọn dẹp |

- `_id` giữ nguyên UUID (BSON binary subtype 4, `uuid-representation=standard`), nên `id`/`fileId` trả về frontend không đổi.
- Unique của PostgreSQL (`WHERE deleted_at IS NULL`) được thay bằng unique index partial `{deleted: false}`
  (slug, skuCode, email, phone, coupon code, size/màu). Service vẫn kiểm tra trước để trả đúng thông báo lỗi cũ;
  index là lớp bảo vệ khi có request đồng thời (trả `400 Duplicate value already exists`).
- Index được tạo khi app khởi động (`createIndex` idempotent, không phải migration dữ liệu).

### Đơn hàng, tồn kho, coupon
`POST /api/orders` chạy trong **một MongoDB transaction** (`MongoTransactionManager`, cần replica set):
1. Đọc SKU/sản phẩm, tính tiền bằng `BigDecimal` (scale 2).
2. Mỗi SKU: `updateOne({_id, deleted:false, status:'ACTIVE', stock: {$gte: qty}}, {$inc: {stock: -qty}})`.
   Không khớp ⇒ lỗi “Not enough stock” ⇒ **abort toàn bộ** (không trừ kho SKU nào, không tạo đơn, không dùng coupon).
3. Coupon: `$inc usedCount` chỉ khi `usageLimit` null hoặc `usedCount < usageLimit`.
4. Ghi đơn cùng `items[]` và lịch sử trạng thái đầu tiên.

Hai đơn cùng SKU chạy đồng thời gây *write conflict* (`TransientTransactionError`); `MongoTransactionRunner` chạy lại
cả transaction (tối đa 10 lần, có backoff). Hết lượt thử ⇒ `409`. Test tích hợp xác nhận: 20 request tranh 5 sản phẩm ⇒
đúng 5 đơn, tồn kho 0; coupon giới hạn 3 với 12 request ⇒ đúng 3 lần dùng.

### Vòng đời file
```
UPLOADING ──(đã lưu object)──> INACTIVE <──(gắn vào bản ghi / bỏ gắn)──> ACTIVE
     │ (upload chết giữa chừng, >1h)        │ (không dùng quá TTL, mặc định 24h)
     └────────────────────> DELETING ──(xóa object thành công)──> xóa metadata
```
- Upload: kiểm tra & xử lý ảnh → **ghi metadata `UPLOADING` trước** → `PutObject` lên R2 → đổi sang `INACTIVE`.
  R2 lỗi ⇒ xóa metadata, trả `503`. MongoDB lỗi sau khi upload ⇒ xóa object (bù trừ); nếu bù trừ cũng lỗi, bản ghi
  `UPLOADING` cũ được job dọn dẹp xử lý sau ⇒ không có object R2 “mồ côi” không được theo dõi.
- Gắn file vào sản phẩm/bài viết…: cập nhật nguyên tử `status ∈ {INACTIVE, ACTIVE} → ACTIVE`; file đang `DELETING`
  hoặc `UPLOADING` không gắn được (báo “please re-upload”).
- Bỏ gắn: chỉ chuyển `INACTIVE` khi không còn tham chiếu sống nào (ảnh gallery, variant, logo/size guide brand,
  banner, cover blog, ảnh collection, ảnh trong HTML qua `htmlFilePaths`). Một file nhiều tham chiếu vẫn `ACTIVE`.
- Dọn dẹp (mỗi 5 phút, mọi dyno đều chạy được): mỗi file được **claim nguyên tử** (`findAndModify` → `DELETING` +
  `leaseUntil`), kiểm tra lại tham chiếu (cả bản ghi đã soft delete, như SQL cũ), xóa object R2 (idempotent), rồi mới
  xóa metadata. Xóa R2 lỗi ⇒ giữ metadata `DELETING`, tăng `deleteAttempts`, thử lại theo backoff (2, 4, 8… phút, tối đa
  24h). Dyno chết giữa chừng ⇒ lease hết hạn, dyno khác tiếp tục.
- TTL file không dùng: `APP_FILE_CLEANUP_INACTIVE_TTL` (mặc định 24h, bản cũ là 10 phút tính từ lúc tạo) — tính từ lần
  đổi trạng thái gần nhất, nên ảnh vừa upload trong lúc soạn bài/sản phẩm không bị xóa.

### Xử lý ảnh (512 MB)
- Nhận diện định dạng bằng magic bytes (JPEG, PNG, GIF, WebP), không tin MIME/đuôi file. HEIC/HEIF/AVIF (iPhone) bị từ
  chối với thông báo rõ ràng (chưa có decoder Java thuần đáng tin cậy, không dùng thư viện native). File không phải ảnh bị từ chối.
- Đọc kích thước từ header, từ chối nếu vượt `APP_IMAGE_MAX_PIXELS` **trước khi decode**.
- Ảnh lớn được decode với *source subsampling* (raster gần kích thước đích thay vì toàn bộ 24–40 MP), resize giữ tỉ lệ,
  không phóng to, tối đa 1600×1600, xoay theo EXIF orientation, ghi lại **không kèm metadata** (EXIF/GPS bị loại bỏ).
  JPEG chất lượng `APP_IMAGE_JPEG_QUALITY` (0.85), PNG giữ trong suốt, WebP → PNG (có alpha) hoặc JPEG. GIF giữ nguyên
  (để không mất animation). Không ép dung lượng cố định.
- Semaphore giới hạn số ảnh xử lý đồng thời (`APP_IMAGE_MAX_CONCURRENT`, mặc định 2); quá 20 giây chờ ⇒ `503`.
- Thư viện: JDK ImageIO + TwelveMonkeys (JPEG CMYK/WebP) + metadata-extractor — đều thuần Java, không cần buildpack native.

### Bộ nhớ JVM trên dyno 512 MB
Procfile:
```
MALLOC_ARENA_MAX=2 java -Xmx${JVM_MAX_HEAP:-200m} -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m
  -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=64m -XX:+ExitOnOutOfMemoryError ...
```
- Heap 200 MB (Heroku mặc định 300 MB cho Basic/Standard-1X): để chừa ~300 MB cho metaspace (~65 MB), code cache,
  thread stack, symbol/GC/native buffer của MongoDB driver/AWS SDK và glibc.
- SerialGC: ít overhead bộ nhớ nhất cho dyno ít CPU. `MALLOC_ARENA_MAX=2` hạn chế phân mảnh malloc.
- Tomcat tối đa 32 thread, MongoDB pool đặt trong URI (`maxPoolSize=10`).
- `ExitOnOutOfMemoryError`: hết heap thì dyno restart sạch thay vì chạy ở trạng thái hỏng.

**Đo trong container của phiên phát triển này (không phải Heroku):** app khởi động ~5 s, RSS sau khởi động ~290 MB;
upload liên tục ảnh JPEG 24 MP (6000×4000) với 1/2/4/8 request đồng thời ⇒ RSS đỉnh ~435 MB (NMT committed ~390 MB),
không OOM, mỗi ảnh ~5–7 s CPU. Với heap 256 MB đỉnh lên ~490 MB — quá sát 512 MB nên mặc định giảm xuống 200 MB.

**Cần đo lại trên Heroku** (CPU/bộ nhớ dyno khác container test):
```bash
heroku labs:enable log-runtime-metrics   # log memory_rss, memory_quota mỗi ~20 s
heroku logs --tail | grep -E "memory_|R14|R15|H12"
# Tạm thời để xem chi tiết native memory:
heroku config:set JAVA_OPTS="-XX:NativeMemoryTracking=summary"
heroku ps:exec --dyno=web.1    # rồi: jcmd $(pgrep java) VM.native_memory summary
```
Nếu thấy R14 (vượt quota): giảm `JVM_MAX_HEAP` (180m), `APP_IMAGE_MAX_CONCURRENT=1`, hoặc `APP_IMAGE_MAX_PIXELS`.
Nếu thấy H12 (request > 30 s) khi nhiều người upload cùng lúc: giảm số request upload song song phía frontend.

## 2. Tạo MongoDB Atlas
1. Tạo project và cluster (gói Free/Flex cho thử nghiệm; production nên dùng gói có backup tự động). Mọi cluster
   Atlas đều là replica set nên hỗ trợ transaction.
2. **Database Access:** tạo user riêng cho app với quyền `readWrite` trên database `fss`. Mật khẩu dài, ngẫu nhiên.
3. **Network Access:** dyno Heroku không có IP cố định. Lựa chọn: cho phép `0.0.0.0/0` (bắt buộc dùng mật khẩu mạnh,
   TLS luôn bật với `mongodb+srv`), hoặc dùng add-on IP tĩnh/Private Space nếu cần allowlist IP.
4. **Connect → Drivers:** lấy URI dạng
   `mongodb+srv://<user>:<password>@<cluster>/fss?retryWrites=true&w=majority&maxPoolSize=10`
   (URL-encode ký tự đặc biệt trong mật khẩu).
5. Nếu sẽ chuyển dữ liệu cũ: **chưa** khởi động API lên database này trước khi chạy `migrate-db` (hoặc đặt
   `APP_SEED_MASTER_DATA=false`), vì API sẽ seed bảng màu/size làm trùng khóa với dữ liệu chuyển sang.

## 3. Tạo Cloudflare R2
1. Cloudflare Dashboard → R2 → **Create bucket** (ví dụ `fss-images`), location tự động.
2. **Domain public:** bucket → Settings → *Custom Domains* → gắn `img.<domain-của-bạn>` (domain phải ở Cloudflare).
   `r2.dev` chỉ nên dùng thử (bị giới hạn tốc độ). Đây là `R2_PUBLIC_BASE_URL` (ví dụ `https://img.example.com`).
   Ảnh được phục vụ tại `https://img.example.com/files/<path>` và có `Cache-Control: public, max-age=31536000, immutable`.
3. **API token:** R2 → *Manage R2 API Tokens* → Create → quyền **Object Read & Write**, giới hạn đúng bucket.
   Ghi lại Access Key ID và Secret Access Key (chỉ hiện một lần) → `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`.
4. Endpoint S3: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` → `R2_ENDPOINT`.
5. Không cần CORS trên bucket cho thẻ `<img>`; chỉ cần nếu frontend `fetch()`/vẽ canvas từ ảnh.
6. Credentials R2 chỉ đặt trên Heroku. Frontend chỉ nhận URL public, không bao giờ nhận key.

Ghi chú kỹ thuật: client dùng AWS SDK for Java v2 với `region=auto`, path-style, tắt `aws-chunked` encoding, chỉ gửi
checksum khi bắt buộc (các SDK mới mặc định gửi CRC mà R2 có thể không hỗ trợ đầy đủ). Test tự động chỉ kiểm tra định
dạng request với S3 giả lập; hãy smoke test với bucket thật sau khi cấu hình (mục 5).

## 4. Heroku
```bash
heroku login
heroku create <app-name>            # hoặc dùng app có sẵn
heroku stack                        # dùng stack hiện hành mặc định
heroku buildpacks:set heroku/gradle # Heroku tự nhận Gradle; lệnh này để chắc chắn

heroku config:set \
  SPRING_PROFILES_ACTIVE=heroku \
  MONGODB_URI='mongodb+srv://...' \
  APP_JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')" \
  APP_CORS_ALLOWED_ORIGINS='https://example.com,https://www.example.com' \
  STORAGE_PROVIDER=r2 \
  R2_ENDPOINT='https://<account-id>.r2.cloudflarestorage.com' \
  R2_BUCKET='fss-images' \
  R2_ACCESS_KEY_ID='...' R2_SECRET_ACCESS_KEY='...' \
  R2_PUBLIC_BASE_URL='https://img.example.com' \
  APP_CONTENT_ALLOWED_IMAGE_HOSTS='example.com,www.example.com'
# Chỉ khi database mới, không chuyển dữ liệu cũ:
heroku config:set APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com APP_BOOTSTRAP_ADMIN_PASSWORD='<>=12 ký tự>'

git push heroku dev_02:main         # build: ./gradlew stage (Java 21 theo system.properties)
heroku ps:scale web=1:basic         # Basic/Standard-1X = 512 MB (Eco ngủ khi không có request)
```
Kiểm tra:
```bash
curl https://<app>.herokuapp.com/api/health          # {"status":"ok"}
curl https://<app>.herokuapp.com/actuator/health     # {"status":"UP"} (không lộ chi tiết)
heroku logs --tail                                   # log ra stdout, có dòng "MongoDB connected replicaSet=..."
heroku releases                                      # danh sách bản deploy
```
Profile `heroku` tự chặn: thiếu `MONGODB_URI`/`APP_JWT_SECRET`/`APP_CORS_ALLOWED_ORIGINS`, JWT secret mặc định hoặc
< 64 ký tự, `STORAGE_PROVIDER=local`, CORS `*`, MongoDB standalone (không transaction). Đăng ký admin công khai bị tắt.
Ứng dụng không chạy migration dữ liệu khi boot (chỉ tạo index và seed master data khi collection rỗng).

## 5. Smoke test sau deploy
1. Đăng nhập admin, lấy access token.
2. `POST /api/admin/files/upload` một ảnh JPEG từ điện thoại → response có `fileId` (UUID) và `url` trên domain R2;
   mở URL được ảnh, ảnh đúng chiều, không còn EXIF GPS.
3. Tạo sản phẩm dùng `fileId` đó, mở trang sản phẩm trên frontend.
4. Đặt một đơn thử với SKU có tồn kho 1, đặt lần hai ⇒ lỗi “Not enough stock”.
5. Upload một ảnh rồi không dùng: sau TTL (24h) object biến mất khỏi bucket.

## 6. Chuyển dữ liệu cũ (PostgreSQL → MongoDB, uploads → R2)
Công cụ: `migration-tool` (chạy tay trên máy có quyền đọc PostgreSQL cũ và thư mục `uploads/`, ví dụ VM cũ).
Không chạy tự động, không ghi vào PostgreSQL, không xóa file local.

```bash
# 0. Backup trước (VM cũ): ./scripts/backup-prod.sh  → backups/<timestamp>/fss_db.dump + uploads.tar.gz
# 1. Tạm dừng ghi (bảo trì frontend/admin) để dữ liệu không đổi trong lúc chuyển.
export PG_URL=jdbc:postgresql://localhost:5432/fss PG_USER=... PG_PASSWORD=...
export MONGODB_URI='mongodb+srv://...' MONGODB_DATABASE=fss

./gradlew :migration-tool:run --args="migrate-db --dry-run"   # đọc & chuyển đổi, không ghi
./gradlew :migration-tool:run --args="migrate-db"             # upsert theo _id, chạy lại an toàn
./gradlew :migration-tool:run --args="verify"                 # đối chiếu, exit code 1 nếu lệch

export UPLOAD_DIR=/opt/fds-backend/uploads R2_ENDPOINT=... R2_BUCKET=... R2_ACCESS_KEY_ID=... R2_SECRET_ACCESS_KEY=...
./gradlew :migration-tool:run --args="sync-files --dry-run"
./gradlew :migration-tool:run --args="sync-files"             # copy uploads/<path> → R2 files/<path>, idempotent
```
- `migrate-db`: giữ nguyên mọi UUID và quan hệ (fileId, categoryId, skuId trong đơn…), copy cả bản ghi đã soft delete,
  tiền chuyển sang Decimal128 scale 2, tính `htmlFilePaths`/`emailKey`/`effectivePrice`, nhúng ảnh sản phẩm, item và
  lịch sử đơn, sản phẩm của collection. Đọc trong một snapshot `REPEATABLE READ`. Từ chối chạy nếu database đích có
  document không đến từ PostgreSQL (trừ khi `--allow-extra-documents`).
- `verify`: so sánh số lượng từng bảng/collection (kể cả mảng nhúng), tổng tồn kho, tổng tiền đơn, tổng lượt dùng
  coupon, số file ACTIVE, và liệt kê tham chiếu file bị treo.
- `sync-files`: bỏ qua object đã có cùng kích thước, chỉ đổi metadata sang `r2` sau khi object tồn tại, báo file thiếu
  trên disk (exit code 1).
- Liên kết ảnh cũ vẫn hoạt động: path giữ nguyên, URL public mới là `R2_PUBLIC_BASE_URL/files/<path>`. HTML cũ chứa
  `/files/...` hoặc `https://<domain-cũ>/files/...` được viết lại sang domain hiện tại khi trả API (domain cũ phải nằm
  trong `APP_CONTENT_ALLOWED_IMAGE_HOSTS`), và `GET <api>/files/<path>` chuyển hướng 301 sang R2.
- Chạy lại `migrate-db` sau khi API đã nhận dữ liệu mới sẽ ghi đè các document trùng id bằng bản PostgreSQL — chỉ
  chạy trước khi chuyển hẳn.
- Độ chính xác thời gian giảm từ micro giây (PostgreSQL) xuống mili giây (BSON Date).

## 7. Rollback
- **Code trên Heroku:** `heroku releases` → `heroku rollback v<N>` (quay về release trước, giữ config vars).
- **Quay lại hệ thống PostgreSQL cũ:** dữ liệu PostgreSQL và `uploads/` không bị công cụ sửa/xóa. Có thể trỏ DNS/
  frontend về VM cũ (nhánh `dev` trước khi merge, hoặc tag release cũ). Lưu ý: đơn hàng/thay đổi phát sinh trên MongoDB
  sau khi chuyển sẽ **không** tự quay về PostgreSQL — cần xuất tay nếu rollback sau khi đã nhận đơn.
- **R2:** object không bị xóa khi rollback; local `uploads/` vẫn còn nguyên.
- **MongoDB:** trước các thay đổi lớn hãy `mongodump --uri "$MONGODB_URI"` (gói Atlas miễn phí không có backup tự động).

## 8. Giới hạn còn lại / việc nên làm tiếp
- Spring Boot 3.3.x đã hết hỗ trợ OSS; nên nâng lên dòng 3.5.x/4.x trong một thay đổi riêng (driver MongoDB đã được
  nâng lên 5.5.1 để tương thích MongoDB 8 trên Atlas).
- HEIC/HEIF chưa được hỗ trợ (bị từ chối). Frontend có thể chuyển sang JPEG trước khi upload, hoặc iPhone chọn
  Camera → Formats → *Most Compatible*.
- GIF không được resize (giữ animation).
- `POST /api/files/upload` vẫn không yêu cầu đăng nhập (giữ như cũ); cân nhắc yêu cầu auth hoặc rate limit để tránh lạm
  dụng dung lượng R2.
- Lọc sản phẩm theo size/màu/giá dựng danh sách id trong bộ nhớ — phù hợp catalog vài nghìn sản phẩm; catalog rất lớn
  nên denormalize (lưu size/giá vào product) và dùng index tổng hợp.
- Tìm kiếm keyword dùng regex không phân biệt hoa thường (tương đương `ILIKE '%...%'`), quét collection; với dữ liệu
  lớn nên dùng Atlas Search.
- Endpoint `/api/admin/logs/stream` đọc file log; trên Heroku log chỉ ra stdout nên endpoint chỉ trả thông báo — dùng
  `heroku logs --tail` hoặc log drain.
- Chưa kiểm chứng với MongoDB Atlas, bucket R2 và dyno Heroku thật (không có credentials trong môi trường phát triển).
