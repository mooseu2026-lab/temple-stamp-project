#!/usr/bin/env bash
# newman = Postman 컬렉션 CLI 러너. npx 가 처음 한 번 내려받는다 (npm 레지스트리 접근 필요)
cd "$(dirname "$0")"
npx --yes newman run temple-stamp.postman_collection.json \
  -e local.postman_environment.json \
  --reporters cli,json --reporter-json-export ../backend/docs/verify/ch2-newman-result.json
