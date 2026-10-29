# Hợp đồng API (cho frontend)

Base URL production: `https://api.thepocketshoes.store/api`. Mọi thời gian trả về theo ISO-8601 UTC (`2026-10-22T03:00:00Z`).
Thời gian gửi lên không kèm offset được hiểu theo giờ `Asia/Ho_Chi_Minh`.

## 1. Envelope

Thành công:

```json
{ "success": true, "message": "OK", "data": { ... } }
```

Lỗi:

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "data": null,
  "errors": [ { "field": "name", "message": "must not be blank" } ]
}
```

- `code` là mã ổn định, FE rẽ nhánh và dịch theo `code`. Các mã đã công bố không bao giờ bị đổi tên.
- `message` chỉ để debug hoặc làm fallback. Nội dung có thể đổi.
- Chỉ lỗi validate mới có `errors[]` (lỗi theo từng trường). Không có lỗi trường thì bỏ hẳn key `errors`.

## 2. HTTP status

| Status | Khi nào |
|---|---|
| 200 | Thành công. Kể cả `POST /coupons/validate` khi mã không dùng được (xem mục 6). |
| 400 | Request sai: validate (`VALIDATION_ERROR`), JSON hỏng (`INVALID_REQUEST_BODY`), id tham chiếu trong body không tồn tại (vd. `CATEGORY_NOT_FOUND` khi tạo sản phẩm), quy tắc nghiệp vụ (`OUT_OF_STOCK`, `COUPON_EXPIRED`...), sai mật khẩu hoặc refresh token (`AUTH_INVALID_CREDENTIALS`, `AUTH_REFRESH_TOKEN_INVALID`). |
| 401 | Chỉ với API admin: thiếu token (`AUTH_REQUIRED`), token sai hoặc hết hạn (`AUTH_TOKEN_INVALID`). FE gọi refresh khi gặp 401. |
| 403 | `ACCESS_DENIED` |
| 404 | Tài nguyên ở **path** không tồn tại hoặc đã xoá: `/products/{slug}`, `/blogs/{slug}`, `/collections/{slug}`, `/pages/{key}`, `/admin/.../{id}`. Dùng để hiển thị trang 404. |
| 409 | Trùng giá trị duy nhất (`SLUG_ALREADY_EXISTS`, `SKU_CODE_ALREADY_EXISTS`, `EMAIL_ALREADY_EXISTS`, `PHONE_ALREADY_EXISTS`...) hoặc ghi đồng thời (`CONCURRENT_UPDATE`, có thể thử lại). |
| 413 / 415 / 429 / 503 | `FILE_TOO_LARGE`/`PAYLOAD_TOO_LARGE`, `FILE_TYPE_NOT_SUPPORTED`/`UNSUPPORTED_MEDIA_TYPE`, `LOG_STREAM_LIMIT_REACHED`, `STORAGE_UNAVAILABLE`/`SERVER_BUSY`. |
| 500 | `INTERNAL_ERROR` |

## 3. Danh mục mã lỗi

Nguồn: `src/main/java/com/fss/backend/common/ErrorCode.java`.

| Nhóm | Mã |
|---|---|
| Chung | `BAD_REQUEST`, `VALIDATION_ERROR`, `INVALID_REQUEST_BODY`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `UNSUPPORTED_MEDIA_TYPE`, `CONFLICT`, `CONCURRENT_UPDATE`, `DUPLICATE_VALUE`, `PAYLOAD_TOO_LARGE`, `TOO_MANY_REQUESTS`, `ACCESS_DENIED`, `SERVICE_UNAVAILABLE`, `INTERNAL_ERROR` |
| Xác thực | `AUTH_REQUIRED`, `AUTH_TOKEN_INVALID`, `AUTH_INVALID_CREDENTIALS`, `AUTH_REFRESH_TOKEN_INVALID`, `AUTH_REGISTRATION_DISABLED`, `ADMIN_USER_NOT_FOUND`, `EMAIL_ALREADY_EXISTS` |
| Catalog | `PRODUCT_NOT_FOUND`, `VARIANT_NOT_FOUND`, `SKU_NOT_FOUND`, `CATEGORY_NOT_FOUND`, `BRAND_NOT_FOUND`, `SIZE_NOT_FOUND`, `SIZE_INACTIVE`, `DEFAULT_SIZE_NOT_CONFIGURED`, `COLOR_NOT_FOUND`, `COLOR_INACTIVE`, `SLUG_ALREADY_EXISTS`, `SKU_CODE_ALREADY_EXISTS`, `SIZE_ALREADY_EXISTS`, `COLOR_ALREADY_EXISTS` |
| Nội dung | `BLOG_POST_NOT_FOUND`, `COLLECTION_NOT_FOUND`, `BANNER_NOT_FOUND`, `SITE_PAGE_NOT_FOUND`, `FAQ_NOT_FOUND` |
| File | `FILE_NOT_FOUND`, `FILE_UNAVAILABLE`, `FILE_EMPTY`, `FILE_TYPE_NOT_SUPPORTED`, `FILE_TOO_LARGE`, `IMAGE_TOO_LARGE`, `IMAGE_CORRUPTED`, `STORAGE_UNAVAILABLE`, `SERVER_BUSY` |
| Khách hàng, đơn hàng | `CUSTOMER_NOT_FOUND`, `PHONE_ALREADY_EXISTS`, `ORDER_NOT_FOUND`, `SKU_UNAVAILABLE`, `OUT_OF_STOCK` |
| Mã giảm giá | `COUPON_NOT_FOUND`, `COUPON_INVALID`, `COUPON_NOT_STARTED`, `COUPON_EXPIRED`, `COUPON_USAGE_LIMIT_REACHED`, `COUPON_MIN_SUBTOTAL`, `COUPON_CODE_ALREADY_EXISTS` |
| Công cụ admin | `LOG_STREAM_LIMIT_REACHED` |

Các mã cho khách đã có message tiếng Việt, FE có thể hiển thị luôn:
- `OUT_OF_STOCK`: `Sản phẩm "<tên>" không đủ số lượng trong kho.`
- `SKU_UNAVAILABLE`: sản phẩm trong giỏ không còn bán.
- Các mã `COUPON_*`.

## 4. Danh sách

Danh sách phân trang trả về `data`:

```json
{ "items": [ ... ], "page": 1, "limit": 20, "total": 57, "totalPages": 3 }
```

- Tham số: `page` (bắt đầu từ 1) và `limit` (1–100, mặc định 20).
- `page`/`limit` sai kiểu hoặc ngoài khoảng trả 400 `VALIDATION_ERROR`, với `errors[].field` là tên tham số.

Chỉ hai endpoint trả mảng thường (`data: [...]`): `GET /products/featured` (tham số `limit`, tối đa 30) và `GET /admin/orders/{id}/status-history`.
`GET /admin/master-data` trả về một object.

## 5. Sản phẩm

### Sắp xếp: `GET /products?sort=`

| Giá trị | Thứ tự |
|---|---|
| `newest` (mặc định khi bỏ trống) | `createdAt` giảm dần |
| `featured` | Sản phẩm nổi bật trước (theo `featuredOrder` tăng dần), sau đó mới nhất |
| `price_asc` / `price_desc` | Theo giá bán thấp nhất (giá sale nếu có, không thì giá gốc). Sản phẩm chưa có giá luôn xếp cuối. |
| `name_asc` | Theo tên. Collation tiếng Việt, không phân biệt hoa thường, so số theo giá trị (`B9` đứng trước `b10`). |

- Server sắp xếp **toàn bộ** kết quả đã lọc rồi mới phân trang.
- Không phân biệt hoa thường, và chấp nhận cả `-` thay cho `_` (`price-asc`).
- Giá trị khác trả 400 `VALIDATION_ERROR`.
- Trang danh sách muốn mặc định "nổi bật" thì FE phải gửi `sort=featured`.

Các bộ lọc khác: `categoryId`, `categorySlug`, `brandId`, `brandSlug`, `gender`, `keyword`, `size`, `color`, `minPrice`, `maxPrice`.
- `minPrice`/`maxPrice` so với giá bán (giá sale nếu có).

### `updatedAt`

Các đối tượng sau đều có `createdAt` và `updatedAt`:
- Sản phẩm: summary trong danh sách, `/products/featured`, chi tiết.
- Bộ sưu tập.
- Bài viết blog.

`updatedAt` của sản phẩm là mốc mới nhất trong các thay đổi sau:
- Sản phẩm.
- Biến thể và SKU, gồm cả tồn kho giảm khi có đơn hàng.

Dùng cho `<lastmod>` của sitemap và để làm mới cache.

### Dữ liệu public

API public (không cần token) chỉ trả những gì đang bán hoặc đã xuất bản.

Sản phẩm:
- Sản phẩm `ACTIVE`, danh mục `ACTIVE`, thương hiệu `ACTIVE` (hoặc không có thương hiệu).
- Chi tiết chỉ gồm biến thể `ACTIVE` và SKU `ACTIVE` thuộc biến thể `ACTIVE` (hoặc SKU không gắn biến thể).
- `minPrice`, `minSalePrice`, `totalStock`, bộ lọc giá/size/màu và sắp xếp theo giá chỉ tính các SKU đó. API admin tính tất cả SKU chưa xoá.
- Đặt hàng một SKU bị ẩn trả 400 `SKU_UNAVAILABLE`.

Nội dung khác:
- Blog: `ACTIVE` và `publishedAt` ≤ hiện tại. Bài hẹn giờ và bài nháp trả 404 theo slug.
- Banner: `ACTIVE`, đang trong khoảng `startsAt`–`endsAt`.
- Bộ sưu tập: `ACTIVE`.
- FAQ: `ACTIVE`.

## 6. Mã giảm giá: `POST /coupons/validate`

Request: `{ "code": "SALE10", "subtotalAmount": 200000 }`. Mã được trim và không phân biệt hoa thường.

Luôn trả 200. Lỗi validate request (thiếu `code` hoặc `subtotalAmount`) vẫn trả 400.

Hợp lệ:

```json
{ "valid": true, "code": null, "message": null, "couponId": "…", "couponCode": "SALE10",
  "discountAmount": 20000.00, "finalAmount": 180000.00 }
```

Không dùng được:

```json
{ "valid": false, "code": "COUPON_MIN_SUBTOTAL", "message": "Đơn hàng cần tối thiểu 500.000 đ để dùng mã này.",
  "couponId": null, "couponCode": null, "discountAmount": 0.00, "finalAmount": 200000.00 }
```

- `code` là **lý do** không dùng được, thuộc `COUPON_NOT_FOUND`, `COUPON_INVALID`, `COUPON_NOT_STARTED`, `COUPON_EXPIRED`, `COUPON_USAGE_LIMIT_REACHED`, `COUPON_MIN_SUBTOTAL`.
- Mã giảm giá đã chuẩn hoá nằm ở `couponCode`.
- Khi đặt hàng với mã không hợp lệ, `POST /orders` trả 400 với cùng `code` và `message`.

## 7. Đặt hàng: `POST /orders`

```json
{ "customerName": "…", "customerPhone": "…", "shippingAddress": "…", "note": "…",
  "couponCode": "SALE10", "shippingFee": 30000,
  "items": [ { "skuId": "…", "quantity": 2 } ] }
```

Response:
- `data.order` gồm `id`, `orderCode` (dạng `SHOE-…`), `subtotalAmount`, `discountAmount`, `shippingFee`, `totalAmount`, `status`, `paymentStatus`, `shippingStatus`.
- `data.items[]` gồm `unitPrice`, `lineTotal`, `size`...

Mã đơn để hiển thị là `data.order.orderCode`.

Server tự tính lại đơn giá (giá sale nếu có), tạm tính, giảm giá và tồn kho từ dữ liệu hiện tại, bỏ qua giá FE gửi lên. Toàn bộ chạy trong một transaction:
- Thiếu hàng trả 400 `OUT_OF_STOCK`, không trừ kho và không tính lượt dùng mã.
- Ghi đồng thời bị xung đột trả 409 `CONCURRENT_UPDATE`.

`shippingFee` hiện vẫn lấy từ request.

## 8. Đăng nhập admin và refresh token

| Endpoint | Body | Ghi chú |
|---|---|---|
| `POST /admin/auth/login` | `{ email, password }` | Trả `{ accessToken, refreshToken }` và set cookie |
| `POST /admin/auth/refresh` | `{ refreshToken }` hoặc trống | Token trong body được ưu tiên, không có thì đọc cookie. Token được xoay vòng: token cũ hết hiệu lực, trả cặp token mới và set cookie mới. |
| `POST /admin/auth/logout` | `{ refreshToken }` hoặc trống | Thu hồi token (body hoặc cookie), xoá cookie. Luôn trả 200. |

Cookie:

```
fss_refresh_token=<token>; Path=/api/admin/auth; Max-Age=<APP_JWT_REFRESH_TOKEN_DAYS ngày>; Secure; HttpOnly; SameSite=Strict
```

Cấu hình:

| Property | Env | Mặc định |
|---|---|---|
| `app.auth.refresh-cookie.secure` | `APP_AUTH_REFRESH_COOKIE_SECURE` | `true` |
| `app.auth.refresh-cookie.same-site` | `APP_AUTH_REFRESH_COOKIE_SAME_SITE` | `Strict` (`Lax`/`None`; `None` bắt buộc `secure=true`) |

- `Strict` dùng được khi trang admin và API cùng site (`*.thepocketshoes.store`).
- Ở local, FE và API phải cùng host (cùng `localhost` hoặc cùng `127.0.0.1`, khác port vẫn được).

Chuyển FE sang dùng cookie (FE hiện tại vẫn chạy được, không bắt buộc đổi ngay):
1. Gọi `login`, `refresh`, `logout` với `credentials: 'include'` (axios: `withCredentials: true`). CORS đã cho phép credentials với các origin được cấu hình.
2. Bỏ lưu `refreshToken` ở `localStorage`. Gọi `refresh` với body trống.
3. Access token chỉ giữ trong bộ nhớ, gửi bằng header `Authorization: Bearer <accessToken>`.

Lỗi:
- Sai mật khẩu: 400 `AUTH_INVALID_CREDENTIALS`.
- Thiếu, sai, hết hạn hoặc đã dùng refresh token: 400 `AUTH_REFRESH_TOKEN_INVALID`. FE chuyển về trang đăng nhập.

## 9. HTML rich text

Server làm sạch `descriptionHtml` (sản phẩm), `contentHtml` (blog, trang nội dung), `answerHtml` (FAQ) và nội dung bộ
sưu tập theo whitelist trước khi lưu:
- Bỏ script, `on*`, `javascript:`.
- Chỉ giữ ảnh từ storage của hệ thống.

FE vẫn nên sanitize khi render dữ liệu cũ.

## 10. Trang nội dung: `/pages/{key}`

Các trang tĩnh do admin soạn. Danh sách khoá cố định:

| `key` | Trang | Tiêu đề mặc định |
|---|---|---|
| `about` | Giới thiệu | `Về chúng tôi` |
| `purchase-shipping` | Tab "Cách mua hàng và hình thức vận chuyển" ở trang sản phẩm | `Cách mua hàng và hình thức vận chuyển` |

```json
{ "key": "about", "title": "Về chúng tôi", "summary": "…", "contentHtml": "<p>…</p>",
  "createdAt": "2026-10-29T03:00:00Z", "updatedAt": "2026-10-29T03:00:00Z" }
```

| Endpoint | Ghi chú |
|---|---|
| `GET /pages/{key}` | Public. Trang chưa từng lưu vẫn trả 200 với tiêu đề mặc định, `summary`, `contentHtml`, `createdAt`, `updatedAt` là `null`. |
| `GET /admin/pages` | Danh sách phân trang gồm tất cả khoá theo thứ tự trên (`page` 1, `limit` = `total` = số khoá). |
| `GET /admin/pages/{key}` | Như bản public. |
| `PUT /admin/pages/{key}` | Body `{ title, summary, contentHtml }`, tạo mới hoặc ghi đè. `title` bắt buộc (≤ 200 ký tự), `summary` ≤ 500 ký tự, rỗng thì lưu `null`. |

- Khoá không thuộc danh sách trả 404 `SITE_PAGE_NOT_FOUND`.
- Không có API xoá trang.

## 11. FAQ: `/faqs`

```json
{ "id": "…", "question": "Bao lâu thì nhận được hàng?", "answerHtml": "<p>…</p>", "sortOrder": 0,
  "status": "ACTIVE", "createdAt": "…", "updatedAt": "…" }
```

| Endpoint | Ghi chú |
|---|---|
| `GET /faqs?page=&limit=` | Public, chỉ FAQ `ACTIVE`. |
| `GET /admin/faqs?status=&keyword=&page=&limit=` | `status` là `ACTIVE`/`INACTIVE`. `keyword` tìm trong câu hỏi, không phân biệt hoa thường. |
| `GET /admin/faqs/{id}` | |
| `POST /admin/faqs` | Body `{ question, answerHtml, sortOrder, status }`. Trả `{ "id": "…" }`. |
| `PUT /admin/faqs/{id}` | Cùng body với `POST`. |
| `DELETE /admin/faqs/{id}` | Xoá mềm. |

- Danh sách sắp xếp theo `sortOrder` tăng dần, rồi `createdAt` tăng dần.
- `question` bắt buộc (≤ 300 ký tự). `answerHtml` bắt buộc, kể cả sau khi làm sạch (chỉ có `<script>` cũng bị từ chối): 400 `VALIDATION_ERROR`.
- `sortOrder` mặc định `0`. `status` mặc định `ACTIVE`.
- Id không tồn tại hoặc đã xoá trả 404 `FAQ_NOT_FOUND`.
