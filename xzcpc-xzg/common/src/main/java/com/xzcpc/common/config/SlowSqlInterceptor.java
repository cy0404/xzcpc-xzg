package com.xzcpc.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;

import java.sql.Statement;
import java.util.Properties;

/**
 * MyBatis 慢 SQL 拦截器。执行超过阈值（默认 500ms）的 SQL 会打印 WARN 日志。
 */
@Slf4j
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private long threshold = 500; // ms

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > threshold) {
                StatementHandler handler = (StatementHandler) invocation.getTarget();
                BoundSql boundSql = handler.getBoundSql();
                String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
                if (sql.length() > 500) sql = sql.substring(0, 500) + "...";
                log.warn("慢SQL [{}ms] {}", elapsed, sql);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String t = properties.getProperty("threshold");
        if (t != null) threshold = Long.parseLong(t);
    }
}
