/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2018 Apollo Foundation
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Apollo Foundation,
 * no part of the Apl software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package apl.http;

import apl.Attachment;
import apl.MonetarySystem;
import apl.Apl;
import apl.Transaction;
import apl.util.Filter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GetExpectedBuyOffers extends APIServlet.APIRequestHandler {

    static final GetExpectedBuyOffers instance = new GetExpectedBuyOffers();

    private GetExpectedBuyOffers() {
        super(new APITag[] {APITag.MS}, "currency", "account", "sortByRate");
    }

    private final Comparator<Transaction> rateComparator = (o1, o2) -> {
        Attachment.MonetarySystemPublishExchangeOffer a1 = (Attachment.MonetarySystemPublishExchangeOffer)o1.getAttachment();
        Attachment.MonetarySystemPublishExchangeOffer a2 = (Attachment.MonetarySystemPublishExchangeOffer)o2.getAttachment();
        return Long.compare(a2.getBuyRateNQT(), a1.getBuyRateNQT());
    };

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        long currencyId = ParameterParser.getUnsignedLong(req, "currency", false);
        long accountId = ParameterParser.getAccountId(req, "account", false);
        boolean sortByRate = "true".equalsIgnoreCase(req.getParameter("sortByRate"));

        Filter<Transaction> filter = transaction -> {
            if (transaction.getType() != MonetarySystem.PUBLISH_EXCHANGE_OFFER) {
                return false;
            }
            if (accountId != 0 && transaction.getSenderId() != accountId) {
                return false;
            }
            Attachment.MonetarySystemPublishExchangeOffer attachment = (Attachment.MonetarySystemPublishExchangeOffer)transaction.getAttachment();
            return currencyId == 0 || attachment.getCurrencyId() == currencyId;
        };

        List<? extends Transaction> transactions = Apl.getBlockchain().getExpectedTransactions(filter);
        if (sortByRate) {
            Collections.sort(transactions, rateComparator);
        }

        JSONObject response = new JSONObject();
        JSONArray offerData = new JSONArray();
        transactions.forEach(transaction -> offerData.add(JSONData.expectedBuyOffer(transaction)));
        response.put("offers", offerData);
        return response;
    }

}
