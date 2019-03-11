/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

/*
 * Copyright © 2018-2019 Apollo Foundation
 */

package com.apollocurrency.aplwallet.apl.core.http.get;

import com.apollocurrency.aplwallet.apl.core.http.APITag;
import com.apollocurrency.aplwallet.apl.core.http.AbstractAPIRequestHandler;
import com.apollocurrency.aplwallet.apl.core.http.JSONData;
import com.apollocurrency.aplwallet.apl.core.peer.Peer;
import com.apollocurrency.aplwallet.apl.core.peer.Peers;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * <p>The GetInboundPeers model will return a list of inbound peers.
 * An inbound peer is a peer that has sent a request to this peer
 * within the previous 30 minutes.</p>
 *
 * <p>Request parameters:</p>
 * <ul>
 * <li>includePeerInfo - Specify 'true' to include the peer information
 * or 'false' to include just the peer address.  The default is 'false'.</li>
 * </ul>
 *
 * <p>Response parameters:</p>
 * <ul>
 * <li>peers - An array of peers</li>
 * </ul>
 *
 * <p>Error Response parameters:</p>
 * <ul>
 * <li>errorCode - model error code</li>
 * <li>errorDescription - model error description</li>
 * </ul>
 */
public final class GetInboundPeers extends AbstractAPIRequestHandler {

    /** GetInboundPeers instance */
    private static class GetInboundPeersHolder {
        private static final GetInboundPeers INSTANCE = new GetInboundPeers();
    }

    public static GetInboundPeers getInstance() {
        return GetInboundPeersHolder.INSTANCE;
    }

    /**
     * Create the GetInboundPeers instance
     */
    private GetInboundPeers() {
        super(new APITag[] {APITag.NETWORK}, "includePeerInfo");
    }

    /**
     * Process the GetInboundPeers model request
     *
     * @param   req                 model request
     * @return                      model response or null
     */
    @Override
    public JSONStreamAware processRequest(HttpServletRequest req) {
        boolean includePeerInfo = "true".equalsIgnoreCase(req.getParameter("includePeerInfo"));
        List<Peer> peers = Peers.getInboundPeers();
        JSONArray peersJSON = new JSONArray();
        if (includePeerInfo) {
            peers.forEach(peer -> peersJSON.add(JSONData.peer(peer)));
        } else {
            peers.forEach(peer -> peersJSON.add(peer.getHost()));
        }
        JSONObject response = new JSONObject();
        response.put("peers", peersJSON);
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
