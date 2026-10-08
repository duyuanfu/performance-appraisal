package com.rawcenter.appraisal.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class UserContext {

    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    public static CurrentUser get() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        CurrentUser u = get();
        return u != null ? u.getUserId() : null;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String realName;
        private String activeRole; // Currently switched working role
        private List<String> roles;
    }
}
