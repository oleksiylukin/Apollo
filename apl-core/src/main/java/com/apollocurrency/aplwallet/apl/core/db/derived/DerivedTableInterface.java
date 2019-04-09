package com.apollocurrency.aplwallet.apl.core.db.derived;

import com.apollocurrency.aplwallet.apl.core.db.DbKey;
import com.apollocurrency.aplwallet.apl.core.db.TransactionalDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Common derived interface functions. It supports rollback, truncate, trim.
 *
 * @author yuriy.larin
 */
public interface DerivedTableInterface<T> {

    void rollback(int height);

    void truncate();

    void trim(int height, TransactionalDataSource dataSource);

    void trim(int height);

    void trim(int height);

    void createSearchIndex(Connection con) throws SQLException;

    default boolean isPersistent() {
        return false;
    }

    default T load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {return null;}

    default void insert(T t, int height) throws SQLException {
        throw new UnsupportedOperationException("unsupported save");
    }

    default boolean delete(T t) {return false;}

}