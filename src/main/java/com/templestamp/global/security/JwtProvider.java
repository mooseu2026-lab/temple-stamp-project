package com.templestamp.global.security;

import com.templestamp.global.config.AppProperties;   // 생성자에서 주입받을 설정 상자
import io.jsonwebtoken.Claims;                        // 토큰 안의 데이터 묶음 타입
import io.jsonwebtoken.JwtException;                  // 서명 위조·만료 시 터지는 예외
import io.jsonwebtoken.Jwts;                          // 토큰 생성·파싱 진입점
import io.jsonwebtoken.io.Decoders;                   // Base64 해독기
import io.jsonwebtoken.security.Keys;                 // 바이트 → SecretKey 변환 도구
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;                                // jjwt 가 시각을 Date 로 받는다

/**
 * 액세스 토큰 발급·검증. HMAC-SHA256, 클레임은 sub(userId)·email·role 만 — tier 는 넣지 않는다.
 * tier 는 바뀔 수 있는 값이라 토큰에 박으면 30분 동안 낡은 값이 돌아다닌다.
 * <p>
 * 리프레시 토큰은 여기서 다루지 않는다. 그쪽은 JWT 가 아니라 난수 문자열이고 DB 에 해시로
 * 저장·회전되므로 AuthService 가 맡는다.
 * <p>
 * 시크릿은 "값" 만이 아니라 "해석 방식" 까지가 스펙이다. .env 의 JWT_SECRET 은 Base64 문자열이고
 * 여기서 Decoders.BASE64 로 해독한 뒤 키를 만든다. 평문으로 취급하면 같은 값을 넣어도
 * 다른 서명이 나온다. 키 생성: {@code openssl rand -base64 32}
 */
@Component                                            // 스프링 빈. SecurityConfig·AuthService 가 주입받는다
public class JwtProvider {

    private final SecretKey key;                      // 서명용 도장. final 이라 생성자에서 한 번 정하고 못 바꿈
    private final long accessSeconds;                 // 1800. 매번 AppProperties 를 뒤지지 않으려고 복사해둠

    public JwtProvider(AppProperties props) {         // 생성자 주입. 서버 켜질 때 딱 한 번 실행
        byte[] secretBytes = Decoders.BASE64.decode(props.jwt().secret());
        //   ① props.jwt().secret()      : 설정 상자에서 Base64 문자열을 꺼냄
        //   ② Decoders.BASE64.decode()  : 그 문자열을 원본 바이트로 해독
        //   ③ Keys.hmacShaKeyFor()      : 그 바이트로 HMAC-SHA 서명용 SecretKey 제작

        if (secretBytes.length < 32) {
            // jjwt 도 WeakKeyException 으로 막아 주지만, 메시지를 우리 말로 바꿔 준다.
            throw new IllegalStateException(
                    "JWT_SECRET 은 Base64 로 32바이트 이상이어야 합니다. 현재 " + secretBytes.length + "바이트");
        }
        if (props.jwt().secret().equals(props.qr().secret())) {
            // QR 은 사찰 현장에 물리적으로 붙어 있어 사진 한 장으로도 새어 나간다.
            // 같은 키를 쓰면 그 순간 QR 하나로 액세스 토큰을 위조할 수 있게 된다.
            throw new IllegalStateException("JWT_SECRET 과 QR_SECRET 은 서로 달라야 합니다.");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessSeconds = props.jwt().accessSeconds();
    }

    /** 로그인·재발급 성공 시 호출. 파라미터 3개가 토큰에 들어갈 정보 전부다 */
    public String createAccessToken(Long userId, String email, String role) {
        Date now = new Date();                        // 두 곳에서 쓰므로 한 번만 구해둔다
                                                      // (두 번 호출하면 미세하게 다른 시각이 됨)
        return Jwts.builder()
                .subject(String.valueOf(userId))      // sub = 토큰 주인공. JWT 표준상 문자열이라 Long → String
                .claim("email", email)                // 키 이름이 parse() 와 같아야 한다
                .claim("role", role)                  // 필터가 "ROLE_" + role 로 스프링 권한을 만든다
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessSeconds * 1000L))
                                                      // 1000L 의 L : int 로 계산하면 오버플로 위험 → long 강제
                .signWith(key)                        // 한 글자만 바꿔도 검증 실패
                                                      // ※ 서명은 암호화가 아니다. 페이로드는 Base64 라 누구나 열어본다
                                                      //   → 그래서 비밀번호를 절대 안 넣는다
                .compact();                           // 헤더.페이로드.서명 을 한 문자열로
    }

    /** LoginResponse.expiresIn 에 그대로 실리는 값(초). 프론트가 미리 재발급을 칠 근거 */
    public long getAccessExpiresInSeconds() {
        return accessSeconds;
    }

    /** 서명·만료가 유효하면 사용자, 아니면 JwtException — 필터가 잡아서 EntryPoint 로 위임 */
    public AuthenticatedUser parse(String token) throws JwtException {
        //   ① Jwts.parser() → ② verifyWith(key) → ③ build()
        //   ④ parseSignedClaims() : 3조각 분해 → 재서명 → 서명 대조 → exp 확인
        //   ⑤ getPayload()        : 검증 통과한 내용물만 꺼냄
        //   ※ DB 를 전혀 안 본다 → 빠르지만, 발급된 액세스 토큰을 서버가 취소할 수 없다
        //     (로그아웃해도 30분간 유효한 이유 — 그래서 만료를 짧게 두고 리프레시로 갱신한다)
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();

        return new AuthenticatedUser(
                Long.valueOf(c.getSubject()),         // sub 는 문자열 "1" → Long 으로 되돌림
                c.get("email", String.class),
                c.get("role", String.class));
    }
}
