package org.entcore.common.postgres;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.SslMode;
import io.vertx.pgclient.pubsub.PgSubscriber;
import io.vertx.sqlclient.*;

import java.time.LocalDateTime;
import java.util.*;

public class PostgresClient {
    private final Vertx vertx;
    private final JsonObject config;
    private PostgresClientPool pool;

    public static PostgresClient create(final Vertx vertx, final JsonObject config) throws Exception{
        if (config.getJsonObject("postgresConfig") != null) {
            final JsonObject postgresqlConfig = config.getJsonObject("postgresConfig");
            final PostgresClient postgresClient = new PostgresClient(vertx, postgresqlConfig);
            return postgresClient;
        }else{
            final String postgresConfig = (String) vertx.sharedData().getLocalMap("server").get("postgresConfig");
            if(postgresConfig!=null){
                final PostgresClient postgresClient = new PostgresClient(vertx, new JsonObject(postgresConfig));
                return postgresClient;
            }else{
                throw new Exception("Missing postgresConfig config");
            }
        }
    }

    public PostgresClient(final Vertx vertx, final JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public static String updatePlaceholders(final JsonObject row, final int startAt, final List<String> columns, final Tuple tuple) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        final List<String> group = new ArrayList<>();
        for (final String col : columns) {
            final Object value = row.getValue(col);
            if(value != null){
                group.add(col+"=$" + placeholderCounter);
                if(value instanceof  JsonObject || value instanceof JsonArray){
                    tuple.addValue(value);
                }else{
                    tuple.addValue(value);
                }
            }
            placeholderCounter++;
        }
        placeholders.add(String.join(",", group));
        return String.join(",", placeholders);
    }

    public static String updatePlaceholdersWithNull(final JsonObject row, final int startAt, final List<String> columns, final Tuple tuple) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        final List<String> group = new ArrayList<>();
        for (final String col : columns) {
            final Object value = row.getValue(col);
            if(value != null){
                group.add(col+"=$" + placeholderCounter);
                if(value instanceof  JsonObject || value instanceof JsonArray){
                    tuple.addValue(value);
                }else{
                    tuple.addValue(value);
                }
            }else{
                group.add(col+"=$" + placeholderCounter);
                tuple.addValue(null);
            }
            placeholderCounter++;
        }
        placeholders.add(String.join(",", group));
        return String.join(",", placeholders);
    }

    public static String insertPlaceholders(final Collection<JsonObject> rows, final int startAt, final List<String> columns) {
        return insertPlaceholders(rows, startAt, columns.toArray(new String[columns.size()]));
    }

    public static String insertPlaceholders(final Collection<JsonObject> rows, final int startAt, final String... column) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        for (final JsonObject row : rows) {
            final List<String> group = new ArrayList<>();
            for (final String col : column) {
                group.add("$" + placeholderCounter);
                placeholderCounter++;
            }
            placeholders.add(String.format("(%s)", String.join(",", group)));
        }
        return String.join(",", placeholders);
    }

    public static String insertPlaceholdersFromMap(final Collection<Map<String, Object>> rows, final int startAt, final String... column) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        for (final Map<String, Object> row : rows) {
            final List<String> group = new ArrayList<>();
            for (final String col : column) {
                group.add("$" + placeholderCounter);
                placeholderCounter++;
            }
            placeholders.add(String.format("(%s)", String.join(",", group)));
        }
        return String.join(",", placeholders);
    }

    public static Tuple insertValues(final Collection<JsonObject> rows, final Tuple tuple, final String... column) {
        for (final JsonObject row : rows) {
            for (final String col : column) {
                final Object value = row.getValue(col);
                if(value instanceof  JsonObject || value instanceof JsonArray){
                    tuple.addValue((value));
                }else{
                    tuple.addValue(value);
                }
            }
        }
        return tuple;
    }

    public static String toInsertQuery(final String table, final JsonObject json) {
        final StringBuilder query = new StringBuilder();
        final List<String> columns = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        for(final String key : json.fieldNames()){
            final int count = values.size()+1;
            columns.add(String.format("\"%s\"",key));
            values.add("$" + count);
        }
        final String columnPart = String.join(",", columns);
        final String valuesPart = String.join(",", values);
        query.append(String.format("INSERT INTO %s (%s) VALUES(%s) RETURNING *",table, columnPart, valuesPart));
        return query.toString();
    }

    public static Tuple toInsertTuple(final JsonObject json) {
        final Tuple tuple = Tuple.tuple();
        for(final String key : json.fieldNames()){
            tuple.addValue(json.getValue(key));
        }
        return tuple;
    }

    public static String toUpdateQuery(final String table, final JsonObject json, final String idColumn) {
        final StringBuilder query = new StringBuilder();
        final List<String> vals = new ArrayList<>();
        final String wherePart = String.format("\"%s\" = $1", idColumn);
        for(final String key : json.fieldNames()){
            if(!idColumn.equalsIgnoreCase(key)){
                final int count = vals.size()+2;
                vals.add(String.format("\"%s\" = $%s", key, count));
            }
        }
        final String valuesPart = String.join(",", vals);
        query.append(String.format("UPDATE %s SET %s WHERE %s RETURNING *",table, valuesPart, wherePart));
        return query.toString();
    }

    public static Tuple toUpdateTuple(final JsonObject json, final String idColumn) {
        final Tuple tuple = Tuple.tuple();
        tuple.addValue(json.getValue(idColumn));
        for(final String key : json.fieldNames()){
            if(!idColumn.equalsIgnoreCase(key)) {
                tuple.addValue(json.getValue(key));
            }
        }
        return tuple;
    }

    public static Tuple toUpdateTuple(final JsonObject json, final String idColumn, final Set<Integer> ids) {
        final Tuple tuple = Tuple.tuple();
        for(final Integer id : ids){
            tuple.addValue(id);
        }
        for(final String key : json.fieldNames()){
            if(!idColumn.equalsIgnoreCase(key)) {
                tuple.addValue(json.getValue(key));
            }
        }
        return tuple;
    }

    public static String toUpdateQuery(final String table, final JsonObject json, final String idColumn, final Set<Integer> ids) {
        final StringBuilder query = new StringBuilder();
        final List<String> vals = new ArrayList<>();
        final String wherePart = String.format("\"%s\" IN (%s)", idColumn, inPlaceholder(ids, 1));
        for(final String key : json.fieldNames()){
            if(!idColumn.equalsIgnoreCase(key)){
                final int count = vals.size()+ids.size()+1;
                vals.add(String.format("\"%s\" = $%s", key, count));
            }
        }
        final String valuesPart = String.join(",", vals);
        query.append(String.format("UPDATE %s SET %s WHERE %s RETURNING *",table, valuesPart, wherePart));
        return query.toString();
    }

    public static Tuple insertValuesWithDefault(final Collection<JsonObject> rows, final Tuple tuple, final Map<String, Object> defaultValues, final List<String> column) {
        return insertValuesWithDefault(rows, tuple, defaultValues, column.toArray(new String[column.size()]));
    }

    public static Tuple insertValuesWithDefault(final Collection<JsonObject> rows, final Tuple tuple, final Map<String, Object> defaultValues, final String... column) {
        for (final JsonObject row : rows) {
            for (final String col : column) {
                final Object value = row.getValue(col, defaultValues.get(col));
                if(value instanceof  JsonObject || value instanceof JsonArray){
                    tuple.addValue((value));
                }else{
                    tuple.addValue(value);
                }
            }
        }
        return tuple;
    }

    public static Tuple insertValuesFromMap(final Collection<Map<String, Object>> rows, final Tuple tuple, final String... column) {
        for (final Map<String, Object> row : rows) {
            for (final String col : column) {
                final Object value = row.get(col);
                if(value instanceof  JsonObject || value instanceof JsonArray){
                    tuple.addValue((value));
                }else{
                    tuple.addValue(value);
                }
            }
        }
        return tuple;
    }

    public static Tuple insertValues(final Collection<JsonObject> rows, final Tuple tuple, final Map<String, Object> defaultValues, final String... column) {
        for (final JsonObject row : rows) {
            for (final String col : column) {
                Object value = row.getValue(col, defaultValues.get(col));
                if (value == null) {
                    value = defaultValues.getOrDefault(col, value);
                }
                if(value instanceof  JsonObject || value instanceof JsonArray){
                    tuple.addValue((value));
                }else{
                    tuple.addValue(value);
                }
            }
        }
        return tuple;
    }

    public static <T> String inPlaceholder(final Collection<T> values, int startAt) {
        int placeholderCounter = startAt;
        final List<String> placeholders = new ArrayList<>();
        for (final T value : values) {
            placeholders.add("$" + placeholderCounter);
            placeholderCounter++;
        }
        return String.join(",", placeholders);
    }

    public static <T> Tuple inTuple(final Tuple tuple, final Collection<T> values) {
        for (final T value : values) {
            if(value instanceof  JsonObject || value instanceof JsonArray){
                tuple.addValue((value));
            }else{
                tuple.addValue(value);
            }
        }
        return tuple;
    }

    public static JsonObject toJson(final Row row) {
        final JsonObject json = new JsonObject();
        for(int i = 0 ; i < row.size(); i++){
            final String key = row.getColumnName(i);
            final Object value = row.getValue(key);
            if (value instanceof UUID) {
                json.put(key, value.toString());
            } else if (value instanceof LocalDateTime) {
                json.put(key, value.toString());
            } else {
                json.put(key, value);
            }
        }
        return json;
    }

    public static JsonObject toJson(final Row row, final RowSet result) {
        final JsonObject json = new JsonObject();
        final List<String> columns = result.columnsNames();
        for (final String key : columns) {
            final Object value = row.getValue(key);
            if (value instanceof UUID) {
                json.put(key, value.toString());
            } else if (value instanceof LocalDateTime) {
                json.put(key, value.toString());
            } else {
                json.put(key, value);
            }
        }
        return json;
    }

    private PgConnectOptions setSsl(final PgConnectOptions options){
        final SslMode sslMode = SslMode.valueOf(config.getString("ssl-mode", "DISABLE"));
        if (!SslMode.DISABLE.equals(sslMode)) {
            options.setSsl(true);
            options.setSslMode(sslMode);
            options.setTrustAll(SslMode.ALLOW.equals(sslMode) || SslMode.PREFER.equals(sslMode) || SslMode.REQUIRE.equals(sslMode));
        }
        return options;
    }

    public PostgresClientChannel getClientChannel() {
        final PgSubscriber pgSubscriber = PgSubscriber.subscriber(vertx, setSsl(new PgConnectOptions()
                .setPort(config.getInteger("port", 5432))
                .setHost(config.getString("host"))
                .setDatabase(config.getString("database"))
                .setUser(config.getString("user"))
                .setPassword(config.getString("password"))
        ));
        return new PostgresClientChannel(pgSubscriber, config);
    }

    public PostgresClientPool getClientPool() {
        return getClientPool(true);
    }

    public PostgresClientPool getClientPool(boolean reuse) {
        if (reuse) {
            if (pool == null) {
                final PgPool pgPool = PgPool.pool(vertx, setSsl(new PgConnectOptions()
                        .setPort(config.getInteger("port", 5432))
                        .setHost(config.getString("host"))
                        .setDatabase(config.getString("database"))
                        .setUser(config.getString("user"))
                        .setPassword(config.getString("password"))),
                        new PoolOptions()
                        .setMaxSize(config.getInteger("pool-size", 10))
                );
                pool = new PostgresClientPool(pgPool, config);
            }
            return pool;
        } else {
            final PgPool pgPool = PgPool.pool(vertx, setSsl(new PgConnectOptions()
                    .setPort(config.getInteger("port", 5432))
                    .setHost(config.getString("host"))
                    .setDatabase(config.getString("database"))
                    .setUser(config.getString("user"))
                    .setPassword(config.getString("password"))),
                    new PoolOptions()
                    .setMaxSize(config.getInteger("pool-size", 10))
            );
            return new PostgresClientPool(pgPool, config);
        }
    }

    public static class PostgresTransaction {
        private static final Logger log = LoggerFactory.getLogger(PostgresClientPool.class);
        private final Transaction pgTransaction;
        private final List<Future> futures = new ArrayList<>();

        PostgresTransaction(final Transaction pgTransaction) {
            this.pgTransaction = pgTransaction;
        }

        public Future<RowSet<Row>> addPreparedQuery(String query, Tuple tuple) {
            final Future<RowSet<Row>> future = Future.future();
            this.pgTransaction.preparedQuery(query).execute(tuple,future.completer());
            futures.add(future);
            return future;
        }

        public Future<Void> notify(final String channel, final String message) {
            final Future<Void> future = Future.future();
            //prepareQuery not works with notify allow only internal safe message
            this.pgTransaction.query(
                    "NOTIFY " + channel + ", '" + message + "'").execute(notified -> {
                        future.handle(notified.mapEmpty());
                        if (notified.failed()) {
                            log.error("Could not notify channel: " + channel);
                        }
                    });
            futures.add(future);
            return future;
        }

        public Future<Void> commit() {
            return CompositeFuture.all(futures).compose(r -> {
                final Promise<Void> future = Promise.promise();
                this.pgTransaction.commit(future);
                return future.future();
            });
        }

        public Future<Void> rollback() {
            final Promise<Void> future = Promise.promise();
            this.pgTransaction.rollback(future);
            return future.future();
        }
    }
}
