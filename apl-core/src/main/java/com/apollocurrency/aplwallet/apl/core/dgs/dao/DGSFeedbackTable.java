/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.dgs.dao;

import com.apollocurrency.aplwallet.apl.core.db.DbKey;
import com.apollocurrency.aplwallet.apl.core.db.EntityDbTable;
import com.apollocurrency.aplwallet.apl.core.db.LongKey;
import com.apollocurrency.aplwallet.apl.core.db.LongKeyFactory;
import com.apollocurrency.aplwallet.apl.core.dgs.DGSFeedback;
import com.apollocurrency.aplwallet.apl.core.dgs.EncryptedDataUtil;
import com.apollocurrency.aplwallet.apl.crypto.EncryptedData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import javax.inject.Singleton;

@Singleton
public class DGSFeedbackTable extends EntityDbTable<DGSFeedback> {
    private static final LongKeyFactory<DGSFeedback> KEY_FACTORY = new LongKeyFactory<>("id") {
        @Override
        public DbKey newKey(DGSFeedback feedback) {
            return new LongKey(feedback.getPurchaseId());
        }
    };
    private static final String TABLE_NAME = "purchase_feedback";


    public DGSFeedbackTable() {
        super(TABLE_NAME, KEY_FACTORY, false);
    }

    protected DGSFeedback load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
        byte[] data = rs.getBytes("feedback_data");
        byte[] nonce = rs.getBytes("feedback_nonce");
        long id = rs.getLong("id");
        int height = rs.getInt("height");
        return new DGSFeedback(id, height, new EncryptedData(data, nonce));
    }

    @Override
    protected void save(Connection con, DGSFeedback feedback, int height) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO purchase_feedback (id, feedback_data, feedback_nonce, "
                + "height, latest) VALUES (?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, feedback.getPurchaseId());
            i = EncryptedDataUtil.setEncryptedData(pstmt, feedback.getFeedbackEncryptedData(), ++i);
            pstmt.setInt(i, height);
            pstmt.executeUpdate();
        }
    }

}
