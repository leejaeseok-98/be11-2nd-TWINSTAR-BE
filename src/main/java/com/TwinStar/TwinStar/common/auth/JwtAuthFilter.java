package com.TwinStar.TwinStar.common.auth;


import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter extends GenericFilterBean {
    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String bearerToken = request.getHeader("Authorization");

        try {
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);

                // 🔹 토큰 검증 (예외 발생 시 catch 블록에서 처리됨)
                if (!jwtUtil.validateToken(token)) {
                    log.warn("[JwtAuthFilter] 유효하지 않은 토큰");
                    throw new JwtException("유효하지 않은 토큰입니다.");
                }

                // 🔹 유저 ID 가져오기
                Long userId = jwtUtil.getUserId(token);
                log.debug("[JwtAuthFilter] JWT 인증 성공 - userId: {}", userId);

                // 🔹 인증 객체 생성
                User userDetails = new User(userId.toString(), "", Collections.emptyList());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 🔹 SecurityContext에 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.error("[JwtAuthFilter] JWT 인증 실패: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Invalid token: " + e.getMessage());
        }
    }
}
