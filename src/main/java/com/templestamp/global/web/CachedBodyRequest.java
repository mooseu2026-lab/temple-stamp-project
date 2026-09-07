package com.templestamp.global.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 본문을 한 번 통째로 읽어 두고, 이후 getInputStream()/getReader() 호출마다 새 스트림을 내준다.
 * CoordinateFieldGuard 가 본문을 검사한 뒤에도 컨트롤러가 같은 본문을 읽을 수 있어야 해서 필요하다.
 */
public class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream delegate = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return delegate.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("비동기 읽기는 지원하지 않습니다.");
            }

            @Override
            public int read() {
                return delegate.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
