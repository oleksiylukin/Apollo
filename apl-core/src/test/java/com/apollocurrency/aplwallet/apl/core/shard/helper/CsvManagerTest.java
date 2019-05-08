/*
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.shard.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.slf4j.LoggerFactory.getLogger;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.apollocurrency.aplwallet.apl.core.account.AccountAssetTable;
import com.apollocurrency.aplwallet.apl.core.account.AccountCurrencyTable;
import com.apollocurrency.aplwallet.apl.core.account.AccountRestrictions;
import com.apollocurrency.aplwallet.apl.core.account.GenesisPublicKeyTable;
import com.apollocurrency.aplwallet.apl.core.account.PhasingOnly;
import com.apollocurrency.aplwallet.apl.core.account.PublicKeyTable;
import com.apollocurrency.aplwallet.apl.core.app.Blockchain;
import com.apollocurrency.aplwallet.apl.core.app.BlockchainImpl;
import com.apollocurrency.aplwallet.apl.core.app.BlockchainProcessorImpl;
import com.apollocurrency.aplwallet.apl.core.app.DefaultBlockValidator;
import com.apollocurrency.aplwallet.apl.core.app.EpochTime;
import com.apollocurrency.aplwallet.apl.core.app.GlobalSyncImpl;
import com.apollocurrency.aplwallet.apl.core.app.KeyStoreService;
import com.apollocurrency.aplwallet.apl.core.app.ReferencedTransactionService;
import com.apollocurrency.aplwallet.apl.core.app.TransactionDaoImpl;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessor;
import com.apollocurrency.aplwallet.apl.core.app.TransactionProcessorImpl;
import com.apollocurrency.aplwallet.apl.core.app.TrimService;
import com.apollocurrency.aplwallet.apl.core.app.VaultKeyStoreServiceImpl;
import com.apollocurrency.aplwallet.apl.core.chainid.BlockchainConfig;
import com.apollocurrency.aplwallet.apl.core.chainid.HeightConfig;
import com.apollocurrency.aplwallet.apl.core.config.DaoConfig;
import com.apollocurrency.aplwallet.apl.core.config.PropertyBasedFileConfig;
import com.apollocurrency.aplwallet.apl.core.config.PropertyProducer;
import com.apollocurrency.aplwallet.apl.core.config.WalletClientProducer;
import com.apollocurrency.aplwallet.apl.core.db.BlockDaoImpl;
import com.apollocurrency.aplwallet.apl.core.db.DatabaseManager;
import com.apollocurrency.aplwallet.apl.core.db.DerivedDbTablesRegistryImpl;
import com.apollocurrency.aplwallet.apl.core.db.DerivedTablesRegistry;
import com.apollocurrency.aplwallet.apl.core.db.KeyFactoryProducer;
import com.apollocurrency.aplwallet.apl.core.db.cdi.transaction.JdbiHandleFactory;
import com.apollocurrency.aplwallet.apl.core.db.dao.ReferencedTransactionDaoImpl;
import com.apollocurrency.aplwallet.apl.core.db.dao.mapper.DexOfferMapper;
import com.apollocurrency.aplwallet.apl.core.db.derived.DerivedTableInterface;
import com.apollocurrency.aplwallet.apl.core.db.derived.MinMaxDbId;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextConfigImpl;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextSearchEngine;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextSearchService;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.FullTextSearchServiceImpl;
import com.apollocurrency.aplwallet.apl.core.db.fulltext.LuceneFullTextSearchEngine;
import com.apollocurrency.aplwallet.apl.core.dgs.dao.DGSPurchaseTable;
import com.apollocurrency.aplwallet.apl.core.phasing.PhasingPollServiceImpl;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollLinkedTransactionTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollResultTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingPollVoterTable;
import com.apollocurrency.aplwallet.apl.core.phasing.dao.PhasingVoteTable;
import com.apollocurrency.aplwallet.apl.core.tagged.TaggedDataServiceImpl;
import com.apollocurrency.aplwallet.apl.core.tagged.dao.DataTagDao;
import com.apollocurrency.aplwallet.apl.core.tagged.dao.TaggedDataDao;
import com.apollocurrency.aplwallet.apl.core.tagged.dao.TaggedDataExtendDao;
import com.apollocurrency.aplwallet.apl.core.tagged.dao.TaggedDataTimestampDao;
import com.apollocurrency.aplwallet.apl.core.transaction.FeeCalculator;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionApplier;
import com.apollocurrency.aplwallet.apl.core.transaction.TransactionValidator;
import com.apollocurrency.aplwallet.apl.data.DbTestData;
import com.apollocurrency.aplwallet.apl.eth.service.EthereumWalletService;
import com.apollocurrency.aplwallet.apl.exchange.dao.DexOfferTable;
import com.apollocurrency.aplwallet.apl.exchange.service.DexService;
import com.apollocurrency.aplwallet.apl.extension.DbExtension;
import com.apollocurrency.aplwallet.apl.extension.TemporaryFolderExtension;
import com.apollocurrency.aplwallet.apl.util.NtpTime;
import com.apollocurrency.aplwallet.apl.util.env.config.Chain;
import com.apollocurrency.aplwallet.apl.util.env.dirprovider.DirProvider;
import com.apollocurrency.aplwallet.apl.util.env.dirprovider.ServiceModeDirProvider;
import com.apollocurrency.aplwallet.apl.util.injectable.PropertiesHolder;
import org.h2.tools.SimpleRowSource;
import org.jboss.weld.junit.MockBean;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.slf4j.Logger;

@EnableWeld
@Execution(ExecutionMode.CONCURRENT)
class CsvManagerTest {
    private static final Logger log = getLogger(CsvManagerTest.class);

    @RegisterExtension
    DbExtension extension = new DbExtension(DbTestData.getDbFileProperties(createPath("csvManagerDb").toAbsolutePath().toString()));
    @RegisterExtension
    static TemporaryFolderExtension temporaryFolderExtension = new TemporaryFolderExtension();

    private NtpTime time = mock(NtpTime.class);
    private LuceneFullTextSearchEngine ftlEngine = new LuceneFullTextSearchEngine(time, temporaryFolderExtension.newFolder("indexDirPath").toPath());
    private FullTextSearchService ftlService = new FullTextSearchServiceImpl(ftlEngine, Set.of("tagged_data", "currency"), "PUBLIC");
    private KeyStoreService keyStore = new VaultKeyStoreServiceImpl(temporaryFolderExtension.newFolder("keystorePath").toPath(), time);
    private BlockchainConfig blockchainConfig = mock(BlockchainConfig.class);
    private HeightConfig config = Mockito.mock(HeightConfig.class);
    private Chain chain = Mockito.mock(Chain.class);
    @WeldSetup
    public WeldInitiator weld = WeldInitiator.from(
            PropertiesHolder.class, BlockchainImpl.class, DaoConfig.class,
            PropertyProducer.class, TransactionApplier.class, ServiceModeDirProvider.class,
            BlockchainProcessorImpl.class, TrimService.class,
            JdbiHandleFactory.class,
            TaggedDataServiceImpl.class, TransactionValidator.class, TransactionProcessorImpl.class,
            GlobalSyncImpl.class, DefaultBlockValidator.class, ReferencedTransactionService.class,
            ReferencedTransactionDaoImpl.class,
            TaggedDataDao.class, DexService.class, DexOfferTable.class, EthereumWalletService.class,
            DexOfferMapper.class, WalletClientProducer.class, PropertyBasedFileConfig.class,
            DataTagDao.class, PhasingPollServiceImpl.class, PhasingPollResultTable.class,
            PhasingPollLinkedTransactionTable.class, PhasingPollVoterTable.class, PhasingVoteTable.class, PhasingPollTable.class,
            KeyFactoryProducer.class, FeeCalculator.class,
            TaggedDataTimestampDao.class,
            TaggedDataExtendDao.class,
            FullTextConfigImpl.class,
            DerivedDbTablesRegistryImpl.class,
            EpochTime.class, BlockDaoImpl.class, TransactionDaoImpl.class)
            .addBeans(MockBean.of(extension.getDatabaseManger(), DatabaseManager.class))
            .addBeans(MockBean.of(extension.getDatabaseManger().getJdbi(), Jdbi.class))
            .addBeans(MockBean.of(mock(TransactionProcessor.class), TransactionProcessor.class))
            .addBeans(MockBean.of(time, NtpTime.class))
            .addBeans(MockBean.of(ftlEngine, FullTextSearchEngine.class))
            .addBeans(MockBean.of(ftlService, FullTextSearchService.class))
            .addBeans(MockBean.of(keyStore, KeyStoreService.class))
            .addBeans(MockBean.of(blockchainConfig, BlockchainConfig.class))
            .build();

    @Inject
    JdbiHandleFactory jdbiHandleFactory;
    @Inject
    private Blockchain blockchain;
    @Inject
    DerivedTablesRegistry registry;
    CsvManager csvManager;

    public CsvManagerTest() throws Exception {}

    private Path createPath(String fileName) {
        try {
            return temporaryFolderExtension.newFolder().toPath().resolve(fileName);
        }
        catch (IOException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @AfterEach
    void cleanup() {
        jdbiHandleFactory.close();
//        registry.getDerivedTables().clear();
    }

    @BeforeEach
    void setUp() {
        doReturn(config).when(blockchainConfig).getCurrentConfig();
        doReturn(chain).when(blockchainConfig).getChain();
        doReturn(UUID.fromString("a2e9b946-290b-48b6-9985-dc2e5a5860a1")).when(chain).getChainId();
//        AccountCurrencyTable.getInstance().init();
//        PhasingOnly.get(Long.parseUnsignedLong("2728325718715804811")); // TODO: finish Arrays !
        AccountAssetTable.getInstance().init();
        GenesisPublicKeyTable.getInstance().init();
        PublicKeyTable publicKeyTable = new PublicKeyTable(blockchain);
        publicKeyTable.init();
        DGSPurchaseTable purchaseTable = new DGSPurchaseTable();
        purchaseTable.init();
    }

    @Test
    void testMinMaxValues() throws SQLException {
        DirProvider dirProvider = mock(DirProvider.class);
        doReturn(temporaryFolderExtension.newFolder("csvExport").toPath()).when(dirProvider).getDataExportDir();
        HashSet<String> excludeColumnNames = new HashSet<>();
        excludeColumnNames.add("DB_ID");
//        excludeColumnNames.add("REFERENCED_TRANSACTION_ID");
//        excludeColumnNames.add("PUBLIC_KEY");
//        excludeColumnNames.add("LATEST");
        csvManager = new CsvManagerImpl(dirProvider.getDataExportDir(), excludeColumnNames);
        csvManager.setOptions("fieldDelimiter=");

        Collection<DerivedTableInterface> result = registry.getDerivedTables(); // extract all derived tables

        assertNotNull(result);
        log.debug("Processing [{}] tables", result.size());
//        assertEquals(12, result.size()); // the real number is higher then initial, it's OK !
        int targetHeight = 8000;
        result.forEach(item -> {
            assertNotNull(item);
            log.debug("Table = '{}'", item.toString());
            int processedCount = 0;
            try (Connection con = extension.getDatabaseManger().getDataSource().getConnection();
                 PreparedStatement pstmt = con.prepareStatement("select * from " + item.toString() + " where db_id > ? and db_id < ? limit ?")) {
                MinMaxDbId minMaxDbId = item.getMinMaxDbId(targetHeight);
                assertTrue(minMaxDbId.getMaxDbId() >= 0);
                log.debug("Table = {}, Min/Max = {} at height = {}", item.toString(), minMaxDbId, targetHeight);

                if (minMaxDbId.getCount() > 0) {
                    do { // do exporting into csv with pagination
                        processedCount = csvManager.append(item.toString(),
                                item.getRangeByDbId(con, pstmt, minMaxDbId, 2), minMaxDbId );
                    } while (processedCount > 0);
                    ((SimpleRowSource)csvManager).close(); // close CSV file

                    dropDataByName(targetHeight, item); // drop exported data only
                }
            } catch (SQLException e) {
                log.error("Exception", e);
            }

/*
            try (ResultSet rs = csvManager.read(item.toString() + ".csv", null, null);
                 Connection con = extension.getDatabaseManger().getDataSource().getConnection()) {
                con.setAutoCommit(false);

                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    for (int i = 0; i < meta.getColumnCount(); i++) {
                        log.debug("{}: {}\n", meta.getColumnLabel(i + 1), rs.getString(i + 1));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
*/
        });
        log.debug("Processed Tables = {}", result);
    }

    private void dropDataByName(int targetHeight, DerivedTableInterface item) {
        // drop data
        try (Connection con = extension.getDatabaseManger().getDataSource().getConnection();
             PreparedStatement pstmt = con.prepareStatement("delete from " + item.toString() + " where db_id < ?")) {
            pstmt.setInt(1, targetHeight);
            int deleted = pstmt.executeUpdate();
            log.debug("Table = {}, deleted = {} at height = {}", item.toString(), deleted, targetHeight);
        } catch (SQLException e) {
            log.error("Exception", e);
        }
    }


}