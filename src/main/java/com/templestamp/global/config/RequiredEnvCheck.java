package com.templestamp.global.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 필수 설정이 없을 때 "그래서 무엇이 없다는 것인지" 를 알려 주고 멈춘다.
 * <p>
 * 이 클래스가 있는 이유는 실제로 겪은 증상 때문이다. application.yml 의
 * {@code password: ${DB_PASSWORD}} 는 값을 못 찾아도 예외를 던지지 않고 문자열
 * "${DB_PASSWORD}" 를 그대로 비밀번호로 넘긴다. 그러면 MySQL 이
 * {@code Access denied for user 'root'@'localhost' (using password: YES)} 를 돌려주고,
 * 로그만 보면 DB 계정이 틀린 것처럼 보인다. 진짜 원인은 .env / .env.local 을 못 읽은 것이고,
 * 대개는 실행 구성의 작업 디렉터리가 프로젝트 루트가 아니어서 생긴다.
 * <p>
 * EnvironmentPostProcessor 는 설정을 다 읽은 직후·빈을 만들기 전에 돌기 때문에,
 * DataSource 가 엉뚱한 값으로 연결을 시도하기 전에 끊을 수 있다.
 * 등록은 META-INF/spring.factories 에서 한다(빈 스캔보다 먼저 도는 지점이라 @Component 로는 안 된다).
 */
public class RequiredEnvCheck implements EnvironmentPostProcessor {

    /** 어느 파일에서 와야 하는 값인지까지 함께 알려 준다. */
    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    /**
     * prod 프로파일에서만 필수인 값. 로컬은 {@code application.yml} 이 실제 기본값을 갖고 있어서
     * 여기 넣으면 개발자 기동이 막힌다 — 그래서 프로파일로 나눈다.
     * <p>
     * 스토리지 값이 비면 {@code storage.bucket} 이 문자열 "" 이 되고,
     * 업로드는 그대로 성공한 것처럼 200 을 돌려준 뒤 아무도 열 수 없는 URL 을 남긴다.
     * 에러가 나지 않는 종류라 배포 뒤 한참 있다가 발견된다.
     */
    private static final Map<String, String> PROD_REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("DB_URL", ".env");
        REQUIRED.put("DB_USERNAME", ".env");
        REQUIRED.put("DB_PASSWORD", ".env.local");
        REQUIRED.put("JWT_SECRET", ".env.local");
        REQUIRED.put("QR_SECRET", ".env.local");

        PROD_REQUIRED.put("STORAGE_BUCKET", "운영 환경변수");
        PROD_REQUIRED.put("STORAGE_ENDPOINT", "운영 환경변수");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> missing = new ArrayList<>();
        Map<String, String> required = new LinkedHashMap<>(REQUIRED);
        if (List.of(environment.getActiveProfiles()).contains("prod")) {
            required.putAll(PROD_REQUIRED);
        }
        required.forEach((key, source) -> {
            if (isUnresolved(environment.getProperty(key))) {
                missing.add(key + " (" + source + ")");
            }
        });
        if (missing.isEmpty()) {
            checkStorageProvider(environment);
            checkPublicBaseUrl(environment);
            return;
        }

        Path workingDir = Paths.get("").toAbsolutePath();
        boolean hasEnv = workingDir.resolve(".env").toFile().exists();
        boolean hasLocal = workingDir.resolve(".env.local").toFile().exists();

        throw new IllegalStateException(String.join("\n",
                "",
                "설정값을 찾지 못했습니다:",
                "  - " + String.join("\n  - ", missing),
                "",
                "현재 작업 디렉터리 : " + workingDir,
                ".env       : " + (hasEnv ? "있음" : "없음  ← 커밋된 파일입니다. 저장소에서 받으세요."),
                ".env.local : " + (hasLocal ? "있음" : "없음  ← 비밀값 파일입니다. 직접 만들어야 합니다."),
                "",
                hint(workingDir, hasEnv, hasLocal),
                ""));
    }

    /**
     * {@code storage.provider} 가 local 이 아니면 멈춘다.
     * <p>
     * 이 검사를 넣은 이유는 설정이 <b>거짓말을 하고 있었기</b> 때문이다. application-prod.yml 에
     * {@code provider: s3} 라고 적혀 있었는데 {@link com.templestamp.upload.ObjectStorageClient} 는
     * 그 값을 한 번도 읽지 않는다 — 언제나 로컬 디렉터리에 쓴다. 설정만 보고 "운영은 S3 로 간다" 고
     * 읽으면 배포한 뒤에야 파일이 서버 디스크에 쌓이고 있다는 것을 알게 된다(최종 점검 F 결함 ③).
     * 구현이 없는 값을 조용히 무시하느니, 그런 값으로는 뜨지 않는 편이 낫다.
     */
    private void checkStorageProvider(ConfigurableEnvironment environment) {
        String provider = environment.getProperty("storage.provider");
        if (provider == null || provider.isBlank() || "local".equalsIgnoreCase(provider.trim())) {
            return;
        }
        throw new IllegalStateException(String.join("\n",
                "",
                "storage.provider=" + provider + " 는 미구현 — local 만 지원합니다.",
                "",
                "ObjectStorageClient 는 provider 값을 읽지 않고 언제나 로컬 디렉터리(storage.local-dir)에 씁니다.",
                "s3 로 적어 두면 설정과 실제가 달라지고, 그 차이는 배포한 뒤에야 드러납니다.",
                "S3 를 붙일 때 put·getBytes·exists·delete 넷을 SDK 로 바꾸고 이 검사를 함께 풉니다.",
                ""));
    }

    /**
     * prod 에서 {@code ebook.public-base-url} 이 localhost 를 가리키면 멈춘다.
     * <p>
     * 이 값은 <b>인증서 QR 이 가리키는 주소</b>다. 로컬 기본값이 남은 채로 배포되면 인증서가
     * 정상적으로 발행되고 PDF 도 멀쩡히 나오는데, 찍힌 QR 만 localhost 를 가리킨다.
     * 종이로 나간 뒤에는 되돌릴 수 없다 — 그래서 뜨기 전에 막는다.
     */
    private void checkPublicBaseUrl(ConfigurableEnvironment environment) {
        if (!List.of(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        String url = environment.getProperty("ebook.public-base-url");
        if (url == null || !url.toLowerCase().contains("localhost")) {
            return;
        }
        throw new IllegalStateException(String.join("\n",
                "",
                "ebook.public-base-url 이 아직 localhost 입니다: " + url,
                "",
                "이 주소는 인증서 QR 에 그대로 찍힙니다. 로컬 값으로 배포하면 종이에 인쇄된 QR 이",
                "localhost 를 가리키고, 인쇄된 뒤에는 고칠 수 없습니다.",
                "운영 환경변수 PUBLIC_BASE_URL 을 배포 도메인으로 채워 주세요.",
                ""));
    }

    private String hint(Path workingDir, boolean hasEnv, boolean hasLocal) {
        if (!hasEnv && !hasLocal) {
            // 둘 다 없으면 파일 문제가 아니라 실행 위치 문제일 가능성이 크다.
            return "→ 실행 구성의 작업 디렉터리를 프로젝트 루트로 맞춰 주세요."
                    + "\n  (IntelliJ: 실행 구성 편집 → Working directory → 프로젝트 루트)";
        }
        if (!hasLocal) {
            return "→ 프로젝트 루트에 .env.local 을 만들고 비밀값을 채워 주세요."
                    + "\n  DB_PASSWORD=..."
                    + "\n  JWT_SECRET=$(openssl rand -base64 32)"
                    + "\n  QR_SECRET=$(openssl rand -base64 32)   # JWT_SECRET 과 반드시 다른 값";
        }
        return "→ 위 파일 안에 해당 키가 채워져 있는지, 키 이름에 오타가 없는지 확인해 주세요.";
    }

    /**
     * 값을 못 찾으면 Spring 은 "${DB_PASSWORD}" 처럼 자리표시자를 그대로 남긴다.
     * 그 상태를 "없음" 으로 본다.
     */
    private boolean isUnresolved(String value) {
        return value == null || value.isBlank() || value.startsWith("${");
    }
}
