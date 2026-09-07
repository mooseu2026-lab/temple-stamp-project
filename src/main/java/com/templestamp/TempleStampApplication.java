package com.templestamp;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UserDetailsServiceAutoConfiguration 을 뺀다.
 * 인증은 전적으로 JWT 로 하고 폼 로그인을 쓰지 않는데, 이 자동설정이 켜져 있으면
 * 기동 때마다 임시 비밀번호를 가진 인메모리 계정이 만들어진다. 쓰이지 않더라도
 * 로그에 비밀번호가 찍히고, 설정이 어긋난 순간 실제로 통과할 수 있는 계정이라 아예 없앤다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan(basePackages = "com.templestamp", annotationClass = Mapper.class)
public class TempleStampApplication {

    public static void main(String[] args) {
        SpringApplication.run(TempleStampApplication.class, args);
    }
}
