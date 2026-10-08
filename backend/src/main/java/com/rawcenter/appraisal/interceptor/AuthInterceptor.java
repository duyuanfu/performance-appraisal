package com.rawcenter.appraisal.interceptor;

import com.rawcenter.appraisal.common.BusinessException;
import com.rawcenter.appraisal.common.JwtUtils;
import com.rawcenter.appraisal.common.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Allow CORS preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = request.getParameter("token");
        }

        if (!StringUtils.hasText(token)) {
            throw new BusinessException(401, "请先登录系统");
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);
            String realName = claims.get("realName", String.class);
            String activeRole = request.getHeader("X-Active-Role");

            UserContext.set(UserContext.CurrentUser.builder()
                    .userId(userId)
                    .username(username)
                    .realName(realName)
                    .activeRole(activeRole)
                    .roles(Collections.emptyList())
                    .build());
            return true;
        } catch (Exception e) {
            throw new BusinessException(401, "登录凭证已失效或无效，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
