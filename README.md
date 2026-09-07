# temple-stamp 백엔드 — 처음 띄우기

```bash
# 1) DB (MySQL 8)
mysql -uroot -p -e "CREATE DATABASE temple_stamp_project DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2) 프로젝트 루트에 .env.local — 저장소에 없다. 직접 만든다
#    DB_PASSWORD / JWT_SECRET / QR_SECRET (뒤 둘은 Base64 32바이트 이상, 서로 달라야 한다)

# 3) 첫 기동은 반드시 seed 프로파일로 — 사찰 110·코스 12가 이때 들어간다
SPRING_PROFILES_ACTIVE=seed ./gradlew bootRun

# 4) 그다음부터는 그냥
./gradlew bootRun

# 5) 검증 한 바퀴 (마지막 줄이 초록이면 통과)
bash backend/docs/verify/all.sh
```

**3번을 건너뛰면 사찰 5곳만 들어가고 검증이 단언 30건 실패로 끝난다.** 자세한 준비·함정은 `backend/docs/정리.md` §2, 전체 지도는 `종합정리.md`.
