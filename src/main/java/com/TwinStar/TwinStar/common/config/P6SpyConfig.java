package com.TwinStar.TwinStar.common.config;

import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import jakarta.annotation.PostConstruct;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class P6SpyConfig {

    @PostConstruct
    public void setLogMessageFormat() {
        P6SpyOptions.getActiveInstance().setLogMessageFormat(P6SpyFormatter.class.getName());
    }

    // 내부 클래스로 Formatter 정의
    public static class P6SpyFormatter implements MessageFormattingStrategy {
        @Override
        public String formatMessage(int connectionId, String now, long elapsed, String category, String prepared, String sql, String url) {
            sql = formatSql(category, sql);
            // 시간(ms) + SQL 출력 (원하는 포맷으로 수정 가능)
            return String.format("[%s] | %d ms | %s", category, elapsed, formatSql(category, sql));
        }

        private String formatSql(String category, String sql) {
            if (sql == null || sql.trim().equals("")) return sql;

            // Only format Statement and PreparedStatement
            if ("statement".equalsIgnoreCase(category) || "prepared".equalsIgnoreCase(category)) {
                String tmpsql = sql.trim().toLowerCase(Locale.ROOT);
                if (tmpsql.startsWith("create") || tmpsql.startsWith("alter") || tmpsql.startsWith("comment")) {
                    sql = FormatStyle.DDL.getFormatter().format(sql);
                } else {
                    sql = FormatStyle.BASIC.getFormatter().format(sql);
                }
            }
            return sql;
        }
    }
}