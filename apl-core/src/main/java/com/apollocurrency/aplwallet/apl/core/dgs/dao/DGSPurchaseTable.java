/*
 *  Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.dgs.dao;

import com.apollocurrency.aplwallet.apl.core.db.DbKey;
import com.apollocurrency.aplwallet.apl.core.db.LongKey;
import com.apollocurrency.aplwallet.apl.core.db.LongKeyFactory;
import com.apollocurrency.aplwallet.apl.core.db.derived.VersionedEntityDbTable;
import com.apollocurrency.aplwallet.apl.core.dgs.EncryptedDataUtil;
import com.apollocurrency.aplwallet.apl.core.dgs.mapper.DGSPurchaseMapper;
import com.apollocurrency.aplwallet.apl.core.dgs.model.DGSPurchase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.inject.Singleton;

@Singleton
public class DGSPurchaseTable extends VersionedEntityDbTable<DGSPurchase> {
    private static final LongKeyFactory<DGSPurchase> KEY_FACTORY = new LongKeyFactory<DGSPurchase>("id") {
        @Override
        public DbKey newKey(DGSPurchase purchase) {
            if (purchase.getDbKey() == null) {
                long id = purchase.getId();
                purchase.setDbKey(new LongKey(id));
            }
            return purchase.getDbKey();
        }
    };
    private static final DGSPurchaseMapper MAPPER = new DGSPurchaseMapper();
    private static final String TABLE = "purchase";

    public DGSPurchaseTable() {
        super(TABLE, KEY_FACTORY);
    }

    @Override
    public DGSPurchase load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
        return MAPPER.map(rs, null);
    }

    @Override
    protected void save(Connection con, DGSPurchase purchase) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO purchase (id, buyer_id, goods_id, seller_id, "
                + "quantity, price, deadline, note, nonce, timestamp, pending, goods, goods_nonce, goods_is_text, refund_note, "
                + "refund_nonce, has_feedback_notes, has_public_feedbacks, discount, refund, height, latest) KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, purchase.getId());
            pstmt.setLong(++i, purchase.getBuyerId());
            pstmt.setLong(++i, purchase.getGoodsId());
            pstmt.setLong(++i, purchase.getSellerId());
            pstmt.setInt(++i, purchase.getQuantity());
            pstmt.setLong(++i, purchase.getPriceATM());
            pstmt.setInt(++i, purchase.getDeadline());
            i = EncryptedDataUtil.setEncryptedData(pstmt, purchase.getNote(), ++i);
            pstmt.setInt(i, purchase.getTimestamp());
            pstmt.setBoolean(++i, purchase.isPending());
            i = EncryptedDataUtil.setEncryptedData(pstmt, purchase.getEncryptedGoods(), ++i);
            pstmt.setBoolean(i, purchase.isGoodsIsText());
            i = EncryptedDataUtil.setEncryptedData(pstmt, purchase.getRefundNote(), ++i);
            pstmt.setBoolean(i, purchase.hasFeedbackNotes());
            pstmt.setBoolean(++i, purchase.hasPublicFeedbacks());
            pstmt.setLong(++i, purchase.getDiscountATM());
            pstmt.setLong(++i, purchase.getRefundATM());
            pstmt.setInt(++i, purchase.getHeight());
            pstmt.executeUpdate();
        }
    }

    @Override
    protected String defaultSort() {
        return " ORDER BY timestamp DESC, id ASC ";
    }

}