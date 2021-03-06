package org.rakam.postgresql.plugin.user;

import com.facebook.presto.sql.ExpressionFormatter;
import com.facebook.presto.sql.tree.Expression;
import com.google.common.collect.ImmutableMap;
import org.rakam.analysis.ConfigManager;
import org.rakam.analysis.MaterializedViewService;
import org.rakam.plugin.MaterializedView;
import org.rakam.plugin.user.UserPropertyMapper;
import org.rakam.postgresql.report.PostgresqlQueryExecutor;
import org.rakam.report.QueryExecutor;

import javax.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.rakam.report.realtime.AggregationType.COUNT;
import static org.rakam.util.ValidationUtil.checkCollection;

public class PostgresqlUserStorage
        extends AbstractPostgresqlUserStorage
{
    public static final String USER_TABLE = "_users";
    private final MaterializedViewService materializedViewService;
    private final PostgresqlQueryExecutor queryExecutor;

    @Inject
    public PostgresqlUserStorage(MaterializedViewService materializedViewService,
            ConfigManager configManager,
            PostgresqlQueryExecutor queryExecutor)
    {
        super(queryExecutor, configManager);
        this.queryExecutor = queryExecutor;
        this.materializedViewService = materializedViewService;
    }

    @Override
    public QueryExecutor getExecutorForWithEventFilter()
    {
        return queryExecutor;
    }

    @Override
    public List<String> getEventFilterPredicate(String project, List<EventFilter> eventFilter)
    {
        List<String> filters = new ArrayList<>(2);

        for (EventFilter filter : eventFilter) {
            StringBuilder builder = new StringBuilder();

            String collection = checkCollection(filter.collection);
            if (filter.aggregation == null) {
                builder.append(format("select \"_user\" from %s.%s", project, collection));
                if (filter.filterExpression != null) {
                    builder.append(" where ").append(new ExpressionFormatter.Formatter(Optional.empty()).process(filter.getExpression(), true));
                }
                // TODO: timeframe
                filters.add((format("id in (%s)", builder.toString())));
            }
            else {
                builder.append(format("select \"_user\" from \"%s\".\"%s\"", project, collection));
                if (filter.filterExpression != null) {
                    builder.append(" where ").append(new ExpressionFormatter.Formatter(Optional.empty()).process(filter.getExpression(), true));
                }
                String field;
                if (filter.aggregation.type == COUNT && filter.aggregation.field == null) {
                    field = "_user";
                }
                else {
                    field = filter.aggregation.field;
                }
                builder.append(" group by \"_user\" ");
                if (filter.aggregation.minimum != null || filter.aggregation.maximum != null) {
                    builder.append(" having ");
                }
                if (filter.aggregation.minimum != null) {
                    builder.append(format(" %s(\"%s\") >= %d ", filter.aggregation.type, field, filter.aggregation.minimum));
                }
                if (filter.aggregation.maximum != null) {
                    if (filter.aggregation.minimum != null) {
                        builder.append(" and ");
                    }
                    builder.append(format(" %s(\"%s\") < %d ", filter.aggregation.type, field, filter.aggregation.maximum));
                }
                filters.add((format("id in (%s)", builder.toString())));
            }
        }

        return filters;
    }

    @Override
    public String getUserTable(String project, boolean isEventFilterActive)
    {
        return project + "." + USER_TABLE;
    }

    @Override
    public void createSegment(String project, String name, String tableName, Expression filterExpression, List<EventFilter> eventFilter, Duration interval)
    {
        StringBuilder builder = new StringBuilder(String.format("select distinct id from %s where ", getUserTable(project, false)));

        if (filterExpression != null) {
            builder.append(filterExpression.toString());
        }

        if (eventFilter != null) {
            builder.append(getEventFilterPredicate(project, eventFilter));
        }

        materializedViewService.create(project, new MaterializedView(tableName,
                "Users who did " + (tableName == null ? "at least one event" : tableName + " event"), builder.toString(), interval, null, ImmutableMap.of()));
    }
}
