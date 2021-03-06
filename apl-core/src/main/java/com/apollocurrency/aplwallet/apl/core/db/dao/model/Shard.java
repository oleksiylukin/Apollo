package com.apollocurrency.aplwallet.apl.core.db.dao.model;

import java.util.Arrays;
import java.util.Objects;

import com.apollocurrency.aplwallet.apl.crypto.Convert;

/**
 * Shard db entity
 */
public class Shard {
    private Long shardId;
    private byte[] shardHash;
    private Long shardState;
    private Integer shardHeight;

    public Shard() {
    }

    public Shard copy() {
        byte[] shardHashCopy = Arrays.copyOf(shardHash, shardHash.length);
        return new Shard(shardId, shardHashCopy, shardHeight);
    }

    public Shard(Integer shardHeight) {
        this.shardHeight = shardHeight;
    }

    public Shard(byte[] shardHash, Integer shardHeight) {
        this.shardHash = shardHash;
        this.shardHeight = shardHeight;
    }

    public Shard(Long shardId, byte[] shardHash, Integer shardHeight) {
        this.shardId = shardId;
        this.shardHash = shardHash;
        this.shardHeight = shardHeight;
    }

    public Shard(Long shardId, String shardHash, Integer shardHeight) {
        this.shardId = shardId;
        this.shardHash = Convert.parseHexString(shardHash);
        this.shardHeight = shardHeight;
    }

    public Shard(Long shardId, byte[] shardHash, Long shardState, Integer shardHeight) {
        this.shardId = shardId;
        this.shardHash = shardHash;
        this.shardState = shardState;
        this.shardHeight = shardHeight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shard shard = (Shard) o;
        return Objects.equals(shardId, shard.shardId) &&
                Arrays.equals(shardHash, shard.shardHash) &&
                Objects.equals(shardHeight, shard.shardHeight);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(shardId, shardHeight);
        result = 31 * result + Arrays.hashCode(shardHash);
        return result;
    }

    public Long getShardId() {
        return shardId;
    }

    public void setShardId(Long shardId) {
        this.shardId = shardId;
    }

    public byte[] getShardHash() {
        return shardHash;
    }

    public void setShardHash(byte[] shardHash) {
        this.shardHash = shardHash;
    }

    public Long getShardState() {
        return shardState;
    }

    public void setShardState(Long shardState) {
        this.shardState = shardState;
    }

    public Integer getShardHeight() {
        return shardHeight;
    }

    public void setShardHeight(Integer shardHeight) {
        this.shardHeight = shardHeight;
    }

    public static ShardBuilder builder() {
        return new ShardBuilder();
    }

    public static final class ShardBuilder {
        private Long shardId;
        private byte[] shardHash;
        private Long shardState;
        private Integer shardHeight;

        private ShardBuilder() {
        }

        public ShardBuilder id(Long shardId) {
            this.shardId = shardId;
            return this;
        }

        public ShardBuilder shardHash(byte[] shardHash) {
            this.shardHash = shardHash;
            return this;
        }

        public ShardBuilder shardState(Long shardState) {
            this.shardState = shardState;
            return this;
        }

        public ShardBuilder shardHeight(Integer shardHeight) {
            this.shardHeight = shardHeight;
            return this;
        }

        public Shard build() {
            return new Shard(shardId, shardHash, shardState, shardHeight);
        }
    }
}
