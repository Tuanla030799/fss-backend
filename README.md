# FSS Backend

Spring Boot backend cho auth, file upload và ecommerce. Project dùng Java 21, MyBatis XML, Flyway và PostgreSQL.

## Module chính
- `auth`: admin auth, JWT, refresh token.
- `auth/account`: admin account management cho phần quản trị.
- `file`: upload, public file URL, cleanup file tạm.
- `catalog/category`: category model/request/controller/service/mapper.
- `catalog/product`: product, image, variant model/request/controller/service/mapper.
- `catalog/sku`: SKU model/request và dữ liệu phục vụ đặt hàng.
- `sales/order`: order, payment/shipping status, status history, controller/service/mapper.
- `promotion/coupon`: coupon validation và CRUD payload/controller/service/mapper.
- `content/banner`: landing banner payload/controller/service/mapper.
- `customer`: customer user management.
- `shared`: request/helper dùng chung.

## Công nghệ
- Java 21
- Spring Boot 3.3
- MyBatis XML
- Flyway
- PostgreSQL 16
- Gradle

## Chạy local
```bash
docker compose up -d db
./gradlew bootRun
```

API chạy mặc định ở `http://localhost:8080`.

## Build và test
```bash
./gradlew clean test
./gradlew clean bootJar
```

## Docker
```bash
docker compose up --build
```

Compose tự dùng profile `docker`, mount `./uploads` vào `/app/uploads` và `./logs` vào `/app/logs`.

## Endpoint chính
- `GET /api/health`
- `POST /api/admin/auth/register`
- `POST /api/admin/auth/login`
- `POST /api/admin/auth/refresh`
- `POST /api/admin/auth/logout`
- `POST /api/files/upload`
- `POST /api/admin/files/upload`
- `GET /files/{yyyy-MM-dd}/{file-name}`
- `GET /api/categories`
- `GET /api/products`
- `GET /api/products/featured`
- `GET /api/products/{slug}`
- `GET /api/landing-banners`
- `POST /api/orders`
- `POST /api/coupons/validate`
- `GET|POST|PUT|DELETE /api/admin/categories`
- `GET|POST|PUT|DELETE /api/admin/products`
- `POST|PUT|DELETE /api/admin/products/{productId}/variants`
- `POST|PUT|DELETE /api/admin/products/{productId}/skus`
- `GET|PATCH /api/admin/orders`
- `PATCH /api/admin/orders/{id}/payment`
- `PATCH /api/admin/orders/{id}/shipping`
- `GET /api/admin/orders/{id}/status-history`
- `GET|POST|PUT|DELETE /api/admin/landing-banners`
- `GET|POST|PUT|DELETE /api/admin/coupons`
- `GET|POST|PUT|DELETE /api/admin/users`
- `GET|POST|PUT|DELETE /api/admin/customers`

## Database migration
Baseline hiện tại nằm trong:
- `src/main/resources/db/migration/V1__init_schema.sql`
- `src/main/resources/db/migration/V2__seed_admin.sql`

Project này là baseline mới, đã bỏ toàn bộ phần copy cũ như `presets/templates` và `design_submissions`.

## Cấu hình quan trọng
- `spring.datasource.*`: PostgreSQL connection.
- `spring.flyway.locations=classpath:db/migration`.
- `mybatis.mapper-locations=classpath*:mappers/*.xml`.
- `app.upload-dir`: thư mục lưu file upload.
- `app.cdn.base-url`: base URL public cho file.
- `app.cors.allowed-origins`: danh sách frontend origin được phép gọi API.
