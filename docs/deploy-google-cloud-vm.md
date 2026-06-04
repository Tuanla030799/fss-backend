# Deploy lên Google Cloud VM

Tài liệu này giả định backend sẽ chạy trên một VM Ubuntu của Google Compute Engine, domain public là `thepocketshoes.store`, DNS/proxy đi qua Cloudflare, và `nginx` container của frontend là reverse proxy public duy nhất. Docker Compose của backend chỉ chạy `api` + `postgres`.

## 1. Chuẩn bị Google Cloud

1. Reserve một static external IP cho VM trong cùng region.
2. Tạo VM Ubuntu LTS, tối thiểu `e2-small`, gắn network tag `web`.
3. Mở firewall cho `tcp:80` và `tcp:443`. Cổng `22` chỉ nên mở cho IP quản trị nếu làm được.

Ví dụ với `gcloud`:

```bash
gcloud compute firewall-rules create fss-allow-web \
  --allow tcp:80,tcp:443 \
  --target-tags web \
  --source-ranges 0.0.0.0/0
```

Nếu cần SSH public tạm thời:

```bash
gcloud compute firewall-rules create fss-allow-ssh \
  --allow tcp:22 \
  --target-tags web \
  --source-ranges 0.0.0.0/0
```

## 2. Trỏ DNS

Tại Cloudflare DNS hoặc nơi quản lý domain, trỏ:

- `A @ -> <STATIC_IP>`
- `A www -> <STATIC_IP>`

Nếu dùng Cloudflare, bật proxy màu cam cho cả `@` và `www` sau khi record đã trỏ đúng IP.

Chỉ chạy bước cấp SSL sau khi `http://thepocketshoes.store` đã resolve đúng về VM.

## 3. Cài phần mềm trên VM

SSH vào VM rồi cài Docker và Certbot. Nếu public web đang đi qua `nginx` container ở repo frontend thì không cần `nginx` host.

### Docker Engine + Compose plugin

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
newgrp docker
```

### Certbot

```bash
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/local/bin/certbot
```

## 4. Deploy backend

Clone repo lên VM:

```bash
git clone <REPO_URL> /opt/fss-backend
cd /opt/fss-backend
```

Tạo file môi trường production:

```bash
cp .env.production.example .env.production
nano .env.production
```

Các biến bắt buộc cần sửa:

- `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_JWT_SECRET`
- `APP_CORS_ALLOWED_ORIGINS`
- `APP_CDN_BASE_URL`
- `BACKEND_PUBLIC_NETWORK`

Khuyến nghị cho domain hiện tại:

```dotenv
APP_CDN_BASE_URL=https://thepocketshoes.store/files
APP_CORS_ALLOWED_ORIGINS=https://thepocketshoes.store,https://www.thepocketshoes.store
APP_AUTH_ALLOW_PUBLIC_ADMIN_REGISTRATION=false
BACKEND_PUBLIC_NETWORK=thepocketshoes_net
```

Tạo Docker network dùng chung với reverse proxy/public nginx của frontend nếu chưa có:

```bash
docker network create thepocketshoes_net || true
```

Chạy deploy:

```bash
chmod +x scripts/deploy-prod.sh
./scripts/deploy-prod.sh
docker compose --env-file .env.production -f docker-compose.prod.yml ps
```

Kiểm tra API local trên VM:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml exec api \
  sh -lc 'wget -qO- http://127.0.0.1:8080/api/health || curl -fsS http://127.0.0.1:8080/api/health'
```

## 5. Kết nối với public nginx container

Backend compose production đã được cấu hình sẵn để:

- giữ PostgreSQL trong network nội bộ riêng
- expose app ở port `8080` chỉ trong Docker network
- join external network `thepocketshoes_net`
- gắn alias `backend`

Vì vậy public `nginx` container ở repo frontend chỉ cần proxy:

- `/` -> frontend service
- `/api/*` -> `http://backend:8080`
- `/files/*` -> `http://backend:8080`

Xác nhận container backend đã join đúng network:

```bash
docker network inspect thepocketshoes_net
```

## 6. Cấp HTTPS ở origin

Nếu repo frontend đang terminate TLS trong `nginx` container thì xử lý certificate ở phía frontend/reverse proxy đó. Backend không cần public `80/443`.

## 7. Cấu hình Cloudflare

Trong Cloudflare dashboard:

1. Ở `DNS`, giữ `@` và `www` ở trạng thái `Proxied`.
2. Ở `SSL/TLS -> Overview`, đặt mode là `Full (strict)`.
3. Ở `Caching`, không cache `/api/*`.
4. Với `/files/*`, có thể để Cloudflare cache ảnh ở edge.

Khuyến nghị cache rules:

1. Rule `Bypass API`
   Expression: `http.request.uri.path starts_with "/api/"`
   Action: `Bypass cache`
2. Rule `Cache uploaded files`
   Expression: `http.request.uri.path starts_with "/files/"`
   Action: `Eligible for cache`

Cloudflare lưu ý thứ tự rule có ảnh hưởng, rule khớp sau cùng sẽ thắng nếu xung đột.

App hiện đã trả static upload qua `/files/**` với cache header dài hạn, nên rule thứ hai sẽ giúp Cloudflare cache ảnh tốt hơn mà không cache nhầm JSON API.

Nếu thay ảnh mà chưa thấy cập nhật ngay ở edge, dùng một trong hai cách:

- Purge theo URL ảnh trên Cloudflare.
- Bật `Development Mode` tạm thời khi đang kiểm tra cache.

## 8. Cập nhật bản mới

```bash
cd /opt/fss-backend
git pull
./scripts/deploy-prod.sh
```

## 9. Lưu ý vận hành

- `docker-compose.prod.yml` không publish backend ra host port. App chỉ `expose 8080` trong Docker network chung với reverse proxy.
- Service backend dùng network alias `backend` trên `thepocketshoes_net`. Nếu frontend reverse proxy đang trỏ sang tên khác, phải thống nhất lại.
- `POST /api/admin/auth/register` đã được khóa trong profile `docker` trừ khi bạn chủ động đặt `APP_AUTH_ALLOW_PUBLIC_ADMIN_REGISTRATION=true`.
- Nếu cần tạo admin đầu tiên bằng API, chỉ bật `APP_AUTH_ALLOW_PUBLIC_ADMIN_REGISTRATION=true` tạm thời, tạo xong thì tắt lại và redeploy ngay.
- Migration `V2__seed_admin.sql` có seed sẵn một admin record. Trước khi dùng production, hãy kiểm tra và thay thế account đó theo quy trình nội bộ của bạn.

## Tài liệu tham khảo

- Google Cloud static IP: https://cloud.google.com/compute/docs/ip-addresses/reserve-static-external-ip-address
- Google Cloud firewall rules: https://cloud.google.com/firewall/docs/using-firewalls
- Docker Engine trên Ubuntu: https://docs.docker.com/installation/ubuntulinux/
- Nginx trên Ubuntu: https://ubuntu.com/server/docs/how-to-install-nginx
- Certbot instructions: https://certbot.eff.org/instructions
- Cloudflare Full (strict): https://developers.cloudflare.com/ssl/origin-configuration/ssl-modes/full-strict/
- Cloudflare original visitor IPs: https://developers.cloudflare.com/support/troubleshooting/restoring-visitor-ips/restoring-original-visitor-ips/
- Cloudflare cache rules: https://developers.cloudflare.com/cache/how-to/cache-rules/settings/
- Cloudflare default cache behavior: https://developers.cloudflare.com/cache/get-started/
- Cloudflare Development Mode: https://developers.cloudflare.com/cache/reference/development-mode/
