# FSS Backend

Spring Boot backend (Java 21, Spring Boot 3.3.5) cho auth, upload ảnh và ecommerce.

- **Database:** MongoDB (Spring Data MongoDB, transaction qua replica set — MongoDB Atlas trên production).
- **Ảnh:** Cloudflare R2 qua S3-compatible API (local disk chỉ dùng khi phát triển).
- **Deploy:** Heroku (Gradle buildpack, dyno 512 MB).

Hướng dẫn triển khai đầy đủ (MongoDB Atlas, Cloudflare R2, Heroku, chuyển dữ liệu, rollback):
[docs/deploy-heroku-mongodb-r2.md](docs/deploy-heroku-mongodb-r2.md).

## Module chính
- `auth`, `auth/account`: admin auth (JWT access token + refresh token), quản lý tài khoản admin.
- `file`: upload ảnh, xử lý ảnh, metadata file, lưu trữ local/R2, dọn file không dùng.
- `catalog/category|brand|product|sku`, `masterdata` (size/màu).
- `sales/order`, `promotion/coupon`, `customer`.
- `content/banner|blog|collection|page|faq|html` (HTML TinyMCE đã sanitize, theo dõi ảnh trong HTML). `page`: trang nội dung
  admin soạn (giới thiệu, cách mua hàng và vận chuyển), `faq`: câu hỏi thường gặp.
- `shared/mongo`: cấu hình MongoDB, index, seed master data, transaction runner.
- `migration-tool/` (Gradle subproject, **không** nằm trong jar deploy): chuyển PostgreSQL cũ → MongoDB và `uploads/` → R2.

## Chạy local

Yêu cầu: JDK 21, Docker.

```bash
docker compose up -d mongo      # MongoDB 7, single-node replica set rs0 (cần cho transaction)
./gradlew bootRun               # API ở http://localhost:8080, ảnh lưu ./uploads, phục vụ tại /files/**
```

Mặc định local: `MONGODB_URI=mongodb://localhost:27017/fss?replicaSet=rs0&directConnection=true`, `STORAGE_PROVIDER=local`,
đăng ký admin công khai bật (`POST /api/admin/auth/register`) để tạo tài khoản đầu tiên. Có thể thay bằng
`APP_BOOTSTRAP_ADMIN_EMAIL` + `APP_BOOTSTRAP_ADMIN_PASSWORD` (chỉ tạo khi chưa có admin nào).

Chạy cả API trong Docker: `docker compose up --build` (profile `docker`).

Thử R2 từ máy local: đặt `STORAGE_PROVIDER=r2` và các biến `R2_*` (xem `.env.heroku.example`).

## Build và test

```bash
./gradlew test        # unit + integration test (Testcontainers: MongoDB 7 replica set, PostgreSQL 16) — cần Docker
./gradlew stage       # build build/libs/app.jar giống Heroku
```

Integration test chạy với MongoDB replica set thật (không mock repository): CRUD/pagination/filter, định dạng UUID và
thời gian, tạo đơn đồng thời không bán vượt tồn kho, rollback khi một bước tạo đơn lỗi, coupon không vượt giới hạn,
upload/resize/metadata/cleanup, migration PostgreSQL → MongoDB. R2 được kiểm tra bằng một S3 endpoint giả lập
in-process (định dạng request), chưa kiểm tra với bucket R2 thật.

## Cấu hình (biến môi trường)

| Biến | Bắt buộc trên Heroku | Ý nghĩa |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `heroku` (Procfile mặc định) | Profile production |
| `MONGODB_URI` | ✔ | Connection string Atlas, nên kèm `retryWrites=true&w=majority&maxPoolSize=10` |
| `MONGODB_DATABASE` | | Tên database (mặc định `fss`) |
| `APP_JWT_SECRET` | ✔ | Chuỗi ngẫu nhiên ≥ 64 ký tự; profile heroku từ chối secret mặc định/ngắn |
| `APP_CORS_ALLOWED_ORIGINS` | ✔ | Danh sách domain frontend, phân tách dấu phẩy, không dùng `*` |
| `STORAGE_PROVIDER` | `r2` | `local` bị chặn trên profile heroku (disk của dyno bị xóa khi restart) |
| `R2_ENDPOINT` | ✔ | `https://<account-id>.r2.cloudflarestorage.com` |
| `R2_BUCKET` | ✔ | Tên bucket |
| `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | ✔ | R2 API token (chỉ ở backend) |
| `R2_PUBLIC_BASE_URL` | ✔ | Domain public của bucket, ví dụ `https://img.example.com`; ảnh có URL `<base>/files/<path>` |
| `APP_CDN_BASE_URL` | | Ghi đè base URL public của ảnh (mặc định `R2_PUBLIC_BASE_URL/files`) |
| `APP_CONTENT_ALLOWED_IMAGE_HOSTS` | | Domain cũ xuất hiện trong HTML bài viết/sản phẩm (để giữ và chuyển hướng ảnh cũ) |
| `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_BOOTSTRAP_ADMIN_PASSWORD` | | Tạo admin đầu tiên nếu DB chưa có admin (mật khẩu ≥ 12 ký tự) |
| `APP_FILE_CLEANUP_INACTIVE_TTL` | | Thời gian giữ file chưa dùng trước khi xóa (mặc định `PT24H`) |
| `APP_IMAGE_MAX_CONCURRENT` | | Số ảnh xử lý đồng thời (mặc định 2) |
| `APP_IMAGE_MAX_PIXELS` | | Giới hạn tổng pixel trước khi decode (mặc định 40 000 000) |
| `APP_IMAGE_JPEG_QUALITY` | | Chất lượng nén JPEG (mặc định 0.85) |
| `APP_UPLOAD_MAX_FILE_SIZE` | | Dung lượng upload tối đa (mặc định `10MB`) |
| `JVM_MAX_HEAP` | | Heap tối đa (mặc định `200m`, xem phần bộ nhớ trong tài liệu deploy) |

Mẫu đầy đủ chỉ có placeholder: [`.env.heroku.example`](.env.heroku.example).

## Endpoint chính
Không đổi so với bản PostgreSQL (cùng path, request, response, định dạng UUID và thời gian UTC ISO-8601):
- `GET /api/health` (và `GET /actuator/health` trả `{"status":"UP"}`; các actuator endpoint khác bị chặn)
- `POST /api/admin/auth/register|login|refresh|logout`
- `POST /api/files/upload`, `POST /api/admin/files/upload` → `{fileId, path, url, resized, width, height}`
- `GET /files/{yyyy-MM-dd}/{file-name}` (local: trả file; R2: chuyển hướng 301 sang URL R2)
- `GET /api/categories|brands|products|products/featured|products/{slug}|collections|blogs|landing-banners|master-data|pages/{key}|faqs`
- `POST /api/orders`, `POST /api/coupons/validate`
- `GET|POST|PUT|PATCH|DELETE /api/admin/...` cho categories, brands, products (+variants, skus), sizes, colors,
  orders (+status, payment, shipping, status-history), landing-banners, coupons, collections, blogs, pages, faqs, users,
  customers.

## Triển khai cũ (Google Cloud VM)
`docker-compose.prod.yml`, `scripts/*.sh`, `.github/workflows/deploy-dev.yml` và `docs/deploy-google-cloud-vm.md`
thuộc cách deploy cũ (VM + Docker). Workflow `deploy-dev.yml` **không còn tự chạy** khi push lên `dev`; chỉ chạy tay
(Actions → Deploy Dev → Run workflow) và cần `.env.production` trên VM có `MONGODB_URI` + biến R2.

## Quy trình làm việc
- Làm tính năng trên nhánh riêng → Pull Request vào `dev` (workflow CI chạy test, không deploy).
- Deploy production khi muốn: `git checkout dev && git pull && git push heroku dev:main`.
- Rollback: `heroku releases -a thepocketshoes` → `heroku rollback v<N> -a thepocketshoes`. PostgreSQL trong compose cũ chỉ còn để backup/đọc dữ liệu cũ khi chuyển đổi.
