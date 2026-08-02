package ch.interlis.generator.reader.ili2db.metrics;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Testseitiger JDBC-Zähler über Java Dynamic Proxy (Spezifikation §50.3).
 *
 * <p>Zählt Metadaten-Aufrufe (getTables/getColumns/getPrimaryKeys/...),
 * Statement-Erzeugung und Ausführungen. SQL wird nur für Testreports
 * normalisiert; es werden keine Datenwerte erfasst.</p>
 */
public final class CountingJdbcProxy {

    private CountingJdbcProxy() {
    }

    public static CountingConnection wrap(Connection delegate) {
        Objects.requireNonNull(delegate, "delegate");
        Map<JdbcInvocationKind, AtomicLong> counts = new EnumMap<>(JdbcInvocationKind.class);
        for (JdbcInvocationKind kind : JdbcInvocationKind.values()) {
            counts.put(kind, new AtomicLong());
        }
        Map<String, AtomicLong> sqlCounts = new LinkedHashMap<>();
        return (CountingConnection) Proxy.newProxyInstance(
            CountingConnection.class.getClassLoader(),
            new Class<?>[] {CountingConnection.class},
            new ConnectionHandler(delegate, counts, sqlCounts));
    }

    private static void count(Map<JdbcInvocationKind, AtomicLong> counts,
                              JdbcInvocationKind kind) {
        counts.get(kind).incrementAndGet();
    }

    private static void countSql(Map<String, AtomicLong> sqlCounts, String sql) {
        if (sql == null) {
            return;
        }
        String normalized = normalizeSql(sql);
        sqlCounts.computeIfAbsent(normalized, ignored -> new AtomicLong()).incrementAndGet();
    }

    /**
     * Nur für Testreports (Spezifikation §50.5): Whitespace normalisieren,
     * String-/Zahlenwerte durch Platzhalter ersetzen.
     */
    public static String normalizeSql(String sql) {
        String normalized = sql.strip().replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("'[^']*'", "?");
        normalized = normalized.replaceAll("\\b\\d+\\b", "?");
        return normalized;
    }

    private static JdbcInvocationSummary snapshot(Map<JdbcInvocationKind, AtomicLong> counts,
                                                  Map<String, AtomicLong> sqlCounts) {
        Map<JdbcInvocationKind, Long> countsCopy = new EnumMap<>(JdbcInvocationKind.class);
        counts.forEach((kind, value) -> countsCopy.put(kind, value.get()));
        Map<String, Long> sqlCopy = new LinkedHashMap<>();
        sqlCounts.forEach((sql, value) -> sqlCopy.put(sql, value.get()));
        return new JdbcInvocationSummary(countsCopy, sqlCopy);
    }

    private static final class ConnectionHandler implements InvocationHandler {

        private static final Pattern SQL_QUERY = Pattern.compile("^\\s*(SELECT|PRAGMA|WITH)", Pattern.CASE_INSENSITIVE);

        private final Connection delegate;
        private final Map<JdbcInvocationKind, AtomicLong> counts;
        private final Map<String, AtomicLong> sqlCounts;

        ConnectionHandler(Connection delegate, Map<JdbcInvocationKind, AtomicLong> counts,
                          Map<String, AtomicLong> sqlCounts) {
            this.delegate = delegate;
            this.counts = counts;
            this.sqlCounts = sqlCounts;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            switch (name) {
                case "snapshot" -> {
                    return snapshot(counts, sqlCounts);
                }
                case "createStatement" -> {
                    count(counts, JdbcInvocationKind.CREATE_STATEMENT);
                    Object result = invokeDelegate(method, args);
                    return wrapStatement(result, null, counts, sqlCounts);
                }
                case "prepareStatement", "prepareCall" -> {
                    count(counts, JdbcInvocationKind.PREPARE_STATEMENT);
                    String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : null;
                    countSql(sqlCounts, sql);
                    Object result = invokeDelegate(method, args);
                    return wrapStatement(result, sql, counts, sqlCounts);
                }
                case "getMetaData" -> {
                    Object result = invokeDelegate(method, args);
                    return wrapMetaData(result, counts);
                }
                case "toString" -> {
                    return "CountingJdbcProxy(" + delegate + ")";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    return invokeDelegate(method, args);
                }
            }
        }

        private Object invokeDelegate(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Object wrapStatement(Object statement, String preparedSql,
                                     Map<JdbcInvocationKind, AtomicLong> counts,
                                     Map<String, AtomicLong> sqlCounts) {
            if (statement instanceof PreparedStatement || statement instanceof Statement) {
                StatementHandler handler = new StatementHandler(statement, counts, sqlCounts);
                Class<?>[] interfaces = statement instanceof CallableStatement
                    ? new Class<?>[] {CallableStatement.class}
                    : statement instanceof PreparedStatement
                        ? new Class<?>[] {PreparedStatement.class}
                        : new Class<?>[] {Statement.class};
                return Proxy.newProxyInstance(Statement.class.getClassLoader(), interfaces, handler);
            }
            return statement;
        }

        private Object wrapMetaData(Object metaData, Map<JdbcInvocationKind, AtomicLong> counts) {
            return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    JdbcInvocationKind kind = switch (name) {
                        case "getTables" -> JdbcInvocationKind.METADATA_GET_TABLES;
                        case "getColumns" -> JdbcInvocationKind.METADATA_GET_COLUMNS;
                        case "getPrimaryKeys" -> JdbcInvocationKind.METADATA_GET_PRIMARY_KEYS;
                        case "getImportedKeys" -> JdbcInvocationKind.METADATA_GET_IMPORTED_KEYS;
                        case "getExportedKeys" -> JdbcInvocationKind.METADATA_GET_EXPORTED_KEYS;
                        case "toString" -> null;
                        default -> null;
                    };
                    if (kind != null) {
                        count(counts, kind);
                    }
                    if ("toString".equals(name)) {
                        return "CountingMetaData(" + metaData + ")";
                    }
                    return method.invoke(metaData, args);
                });
        }
    }

    private static final class StatementHandler implements InvocationHandler {

        private final Object delegate;
        private final Map<JdbcInvocationKind, AtomicLong> counts;
        private final Map<String, AtomicLong> sqlCounts;

        StatementHandler(Object delegate, Map<JdbcInvocationKind, AtomicLong> counts,
                         Map<String, AtomicLong> sqlCounts) {
            this.delegate = delegate;
            this.counts = counts;
            this.sqlCounts = sqlCounts;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            switch (name) {
                case "executeQuery" -> {
                    count(counts, JdbcInvocationKind.EXECUTE_QUERY);
                    if (args != null && args.length > 0 && args[0] instanceof String sql) {
                        countSql(sqlCounts, sql);
                    }
                }
                case "executeUpdate", "execute", "executeLargeUpdate" -> {
                    count(counts, JdbcInvocationKind.EXECUTE_UPDATE);
                    if (args != null && args.length > 0 && args[0] instanceof String sql) {
                        countSql(sqlCounts, sql);
                    }
                }
                case "toString" -> {
                    return "CountingStatement(" + delegate + ")";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    // no-op
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
