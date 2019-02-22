
package com.apollocurrency.aplwallet.apl.core.consensus.forging;


import com.apollocurrency.aplwallet.apl.core.app.Convert2;
import com.apollocurrency.aplwallet.apl.crypto.Convert;
import com.apollocurrency.aplwallet.apl.crypto.Crypto;

import java.math.BigInteger;

public class Generator implements Comparable<Generator> {
    private final byte[] keySeed;
    private final byte[] publicKey;
    private final Long accountId;
    private volatile long hitTime;
    private volatile BigInteger hit;
    private volatile BigInteger effectiveBalance;
    private volatile long deadline;

    public byte[] getKeySeed() {
        return keySeed;
    }

    @Override
    public String toString() {
        return "Generator{" +
                "accountId=" + Convert2.rsAccount(accountId) +
                ", effectiveBalance=" + effectiveBalance +
                ", deadline=" + deadline +
                '}';
    }

    public Generator(byte[] keySeed) {
        this.keySeed = keySeed;
        this.publicKey = Crypto.getPublicKey(keySeed);
        this.accountId = Convert.getId((publicKey));
    }

    public Generator(byte[] keySeed, byte[] publicKey, Long accountId) {
        this.keySeed = keySeed;
        this.publicKey = publicKey;
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public long getHitTime() {
        return hitTime;
    }

    public BigInteger getHit() {
        return hit;
    }

    public BigInteger getEffectiveBalance() {
        return effectiveBalance;
    }

    public long getDeadline() {
        return deadline;
    }

    public void setHitTime(long hitTime) {
        this.hitTime = hitTime;
    }

    public void setHit(BigInteger hit) {
        this.hit = hit;
    }

    public void setEffectiveBalance(BigInteger effectiveBalance) {
        this.effectiveBalance = effectiveBalance;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    @Override
    public int compareTo(Generator g) {
        int i = this.hit.multiply(g.getEffectiveBalance()).compareTo(g.getHit().multiply(this.getEffectiveBalance()));
        if (i != 0) {
            return i;
        }
        return Long.compare(accountId, g.getAccountId());
    }
}