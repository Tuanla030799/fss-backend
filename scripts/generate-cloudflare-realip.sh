#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${1:-}"

render() {
    curl -fsSL https://www.cloudflare.com/ips-v4 | sed 's#^#set_real_ip_from #; s#$#;#'
    curl -fsSL https://www.cloudflare.com/ips-v6 | sed 's#^#set_real_ip_from #; s#$#;#'
    printf '%s\n' 'real_ip_header CF-Connecting-IP;'
    printf '%s\n' 'real_ip_recursive on;'
}

if [[ -n "${OUTPUT_PATH}" ]]; then
    render > "${OUTPUT_PATH}"
else
    render
fi
