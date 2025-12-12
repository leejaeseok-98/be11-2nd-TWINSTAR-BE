package com.TwinStar.TwinStar.common.config;

import com.TwinStar.TwinStar.common.auth.CustomAuthenticationFilter;
import com.TwinStar.TwinStar.common.auth.JwtAuthFilter;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {
    private final JwtAuthFilter authFilter;
    private final UserRepository userRepository;

    public SecurityConfig(JwtAuthFilter authFilter, UserRepository userRepository) {
        this.authFilter = authFilter;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity,CustomAuthenticationFilter customAuthenticationFilter) throws Exception {
        return httpSecurity
                .cors(c -> c.configurationSource(corsConfiguration()))
                .csrf(AbstractHttpConfigurer::disable) // CSRF 보호 비활성화 (JWT 사용 시 필요 없음)
                .httpBasic(AbstractHttpConfigurer::disable) // HTTP Basic 인증 비활성화
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 미사용 (Stateless)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/user/create", "/user/doLogin", "/user/refresh-token","/connect/**","/user/check-email/{email}","/user/check-nickname/{nickname}","/post/health","/actuator/prometheus").permitAll() // 인증 없이 접근 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요
                )
                // 정지된 사용자 로그인 차단 필터 추가
                .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class) // JWT 필터 추가
                .build();
    }

    private CorsConfigurationSource corsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000","https://server.alexandrelax.store","https://www.alexandrelax.store")); // 프론트엔드 도메인
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS","PATCH")); // 허용할 HTTP 메서드
        configuration.setAllowedHeaders(List.of("*")); // 모든 헤더 허용
        configuration.setAllowCredentials(true); // 쿠키, 인증 정보 포함 허용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 URL에 CORS 설정 적용
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

//    이 객체를 이용해서 로그인 시 사용자의 아이디와 비밀번호를 검증. authenticationManager에 사용자 정보 조회와 비밀번호검증이 자동으로 포함되어 있음
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

//    로그인을 가로채고 정지된 사용자인지 확인하는 필터
    @Bean
    public CustomAuthenticationFilter customAuthenticationFilter(AuthenticationManager authenticationManager) {
        return new CustomAuthenticationFilter(authenticationManager, userRepository);
    }
}