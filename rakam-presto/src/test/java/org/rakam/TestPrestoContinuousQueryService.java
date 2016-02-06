package org.rakam;

import com.google.common.eventbus.EventBus;
import org.rakam.analysis.InMemoryQueryMetadataStore;
import org.rakam.analysis.JDBCMetastore;
import org.rakam.analysis.JDBCPoolDataSource;
import org.rakam.analysis.TestContinuousQueryService;
import org.rakam.collection.event.FieldDependencyBuilder;
import org.rakam.collection.event.metastore.Metastore;
import org.rakam.event.TestingEnvironment;
import org.rakam.plugin.ContinuousQueryService;
import org.rakam.report.PrestoContinuousQueryService;
import org.rakam.report.PrestoQueryExecutor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

public class TestPrestoContinuousQueryService extends TestContinuousQueryService {

    private PrestoContinuousQueryService continuousQueryService;
    private JDBCMetastore metastore;
    private TestingEnvironment testEnvironment;

    @BeforeMethod
    public void before() throws Exception {
        metastore.clearCache();
    }

    @BeforeSuite
    public void setUp() throws Exception {
        testEnvironment = new TestingEnvironment();

        metastore = new JDBCMetastore(
                JDBCPoolDataSource.getOrCreateDataSource(testEnvironment.getPostgresqlConfig()),
                testEnvironment.getPrestoConfig(), new EventBus(), new FieldDependencyBuilder().build());
        metastore.setup();

        InMemoryQueryMetadataStore queryMetadataStore = new InMemoryQueryMetadataStore();

        PrestoQueryExecutor prestoQueryExecutor = new PrestoQueryExecutor(testEnvironment.getPrestoConfig(), metastore);

        continuousQueryService = new PrestoContinuousQueryService(queryMetadataStore,
                prestoQueryExecutor, testEnvironment.getPrestoConfig());
    }

    @Override
    public ContinuousQueryService getContinuousQueryService() {
        return continuousQueryService;
    }

    @Override
    public Metastore getMetastore() {
        return metastore;
    }
}