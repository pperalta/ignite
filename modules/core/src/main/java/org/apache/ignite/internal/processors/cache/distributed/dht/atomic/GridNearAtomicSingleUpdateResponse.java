/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.distributed.dht.atomic;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.GridDirectCollection;
import org.apache.ignite.internal.GridDirectTransient;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheReturn;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.util.GridLongList;
import org.apache.ignite.internal.util.tostring.GridToStringInclude;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.plugin.extensions.communication.MessageCollectionItemType;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;
import org.jetbrains.annotations.Nullable;

import java.io.Externalizable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * DHT atomic cache near update response.
 */
public class GridNearAtomicSingleUpdateResponse extends GridNearAtomicAbstractUpdateResponse {
    /** */
    private static final long serialVersionUID = 0L;

    /** Node ID this reply should be sent to. */
    @GridDirectTransient
    private UUID nodeId;

    /** Future version. */
    private GridCacheVersion futVer;

    /** Update error. */
    @GridDirectTransient
    private volatile IgniteCheckedException err;

    /** Serialized error. */
    private byte[] errBytes;

    /** Return value. */
    @GridToStringInclude
    private GridCacheReturn ret;

    /** Failed keys. */
    @GridToStringInclude
    private volatile KeyCacheObject failedKey;

    /** Keys that should be remapped. */
    @GridToStringInclude
    private KeyCacheObject remapKey;

    /** Indexes of keys for which values were generated on primary node (used if originating node has near cache). */
    @GridDirectCollection(int.class)
    private List<Integer> nearValsIdxs;

    /** Indexes of keys for which update was skipped (used if originating node has near cache). */
    @GridDirectCollection(int.class)
    private List<Integer> nearSkipIdxs;

    /** Values generated on primary node which should be put to originating node's near cache. */
    @GridToStringInclude
    @GridDirectCollection(CacheObject.class)
    private List<CacheObject> nearVals;

    /** Version generated on primary node to be used for originating node's near cache update. */
    private GridCacheVersion nearVer;

    /** Near TTLs. */
    private GridLongList nearTtls;

    /** Near expire times. */
    private GridLongList nearExpireTimes;

    /**
     * Empty constructor required by {@link Externalizable}.
     */
    public GridNearAtomicSingleUpdateResponse() {
        // No-op.
    }

    /**
     * @param cacheId Cache ID.
     * @param nodeId Node ID this reply should be sent to.
     * @param futVer Future version.
     * @param addDepInfo Deployment info flag.
     */
    public GridNearAtomicSingleUpdateResponse(int cacheId, UUID nodeId, GridCacheVersion futVer, boolean addDepInfo) {
        assert futVer != null;

        this.cacheId = cacheId;
        this.nodeId = nodeId;
        this.futVer = futVer;
        this.addDepInfo = addDepInfo;
    }

    /** {@inheritDoc} */
    @Override public UUID nodeId() {
        return nodeId;
    }

    /** {@inheritDoc} */
    @Override public void nodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion futureVersion() {
        return futVer;
    }

    /** {@inheritDoc} */
    @Override public void error(IgniteCheckedException err){
        this.err = err;
    }

    /** {@inheritDoc} */
    @Override public IgniteCheckedException error() {
        return err;
    }

    /** {@inheritDoc} */
    @Override public int failedKeysCount() {
        return failedKey == null ? 0 : 1;
    }

    /** {@inheritDoc} */
    @Override public KeyCacheObject failedKey(int idx) {
        assert idx == 0;

        return failedKey;
    }

    /** {@inheritDoc} */
    @Override public GridCacheReturn returnValue() {
        return ret;
    }

    /**
     * @param ret Return value.
     */
    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public void returnValue(GridCacheReturn ret) {
        this.ret = ret;
    }

    /** {@inheritDoc} */
    @Override public void remapKeys(GridNearAtomicAbstractUpdateRequest req) {
        assert req instanceof GridNearAtomicSingleUpdateRequest;

        remapKey = req.key(0);
    }

    /** {@inheritDoc} */
    @Override public KeyCacheObject remapKey(int idx) {
        assert idx == 0;

        return remapKey;
    }

    /** {@inheritDoc} */
    @Override public int remapKeysCount() {
        return remapKey == null ? 0 : 1;
    }

    /** {@inheritDoc} */
    @Override public void addNearValue(int keyIdx, @Nullable CacheObject val, long ttl, long expireTime) {
        if (nearValsIdxs == null) {
            nearValsIdxs = new ArrayList<>();
            nearVals = new ArrayList<>();
        }

        addNearTtl(keyIdx, ttl, expireTime);

        nearValsIdxs.add(keyIdx);
        nearVals.add(val);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Override public void addNearTtl(int keyIdx, long ttl, long expireTime) {
        if (ttl >= 0) {
            if (nearTtls == null) {
                nearTtls = new GridLongList(16);

                for (int i = 0; i < keyIdx; i++)
                    nearTtls.add(-1L);
            }
        }

        if (nearTtls != null)
            nearTtls.add(ttl);

        if (expireTime >= 0) {
            if (nearExpireTimes == null) {
                nearExpireTimes = new GridLongList(16);

                for (int i = 0; i < keyIdx; i++)
                    nearExpireTimes.add(-1);
            }
        }

        if (nearExpireTimes != null)
            nearExpireTimes.add(expireTime);
    }

    /** {@inheritDoc} */
    @Override public long nearExpireTime(int idx) {
        if (nearExpireTimes != null) {
            assert idx >= 0 && idx < nearExpireTimes.size();

            return nearExpireTimes.get(idx);
        }

        return -1L;
    }

    /** {@inheritDoc} */
    @Override public long nearTtl(int idx) {
        if (nearTtls != null) {
            assert idx >= 0 && idx < nearTtls.size();

            return nearTtls.get(idx);
        }

        return -1L;
    }

    /** {@inheritDoc} */
    @Override public void nearVersion(GridCacheVersion nearVer) {
        this.nearVer = nearVer;
    }

    /** {@inheritDoc} */
    @Override public GridCacheVersion nearVersion() {
        return nearVer;
    }

    /** {@inheritDoc} */
    @Override public void addSkippedIndex(int keyIdx) {
        if (nearSkipIdxs == null)
            nearSkipIdxs = new ArrayList<>();

        nearSkipIdxs.add(keyIdx);

        addNearTtl(keyIdx, -1L, -1L);
    }

    /** {@inheritDoc} */
    @Override public boolean isNearSkippedIndex(int idx) {
        return nearSkipIdxs != null && nearSkipIdxs.contains(idx);
    }

    /** {@inheritDoc} */
    @Override public boolean isNearValueIndex(int idx) {
        return nearValsIdxs != null && nearValsIdxs.contains(idx);
    }

    /** {@inheritDoc} */
    @Override @Nullable public CacheObject nearValue(int idx) {
        return nearVals.get(idx);
    }

    /** {@inheritDoc} */
    @Override public synchronized void addFailedKey(KeyCacheObject key, Throwable e) {
        failedKey = key;

        setFailedKeysError(e);
    }

    /** {@inheritDoc} */
    @Override public synchronized void addFailedKeys(Collection<KeyCacheObject> keys, Throwable e) {
        if (keys != null) {
            assert keys.size() == 1;

            failedKey = keys.iterator().next();
        }

        setFailedKeysError(e);
    }

    /** {@inheritDoc} */
    @Override public synchronized void addFailedKeys(GridNearAtomicAbstractUpdateRequest req, Throwable e) {
        assert req instanceof GridNearAtomicSingleUpdateRequest;

        failedKey = req.key(0);

        setFailedKeysError(e);
    }

    /**
     * Set failed keys error.
     *
     * @param e Error.
     */
    private void setFailedKeysError(Throwable e) {
        if (err == null)
            err = new IgniteCheckedException("Failed to update keys on primary node.");

        err.addSuppressed(e);
    }

    /** {@inheritDoc}
     * @param ctx*/
    @Override public void prepareMarshal(GridCacheSharedContext ctx) throws IgniteCheckedException {
        super.prepareMarshal(ctx);

        if (err != null && errBytes == null)
            errBytes = ctx.marshaller().marshal(err);

        GridCacheContext cctx = ctx.cacheContext(cacheId);

        prepareMarshalCacheObject(failedKey, cctx);
        prepareMarshalCacheObject(remapKey, cctx);

        prepareMarshalCacheObjects(nearVals, cctx);

        if (ret != null)
            ret.prepareMarshal(cctx);
    }

    /** {@inheritDoc} */
    @Override public void finishUnmarshal(GridCacheSharedContext ctx, ClassLoader ldr) throws IgniteCheckedException {
        super.finishUnmarshal(ctx, ldr);

        if (errBytes != null && err == null)
            err = ctx.marshaller().unmarshal(errBytes, U.resolveClassLoader(ldr, ctx.gridConfig()));

        GridCacheContext cctx = ctx.cacheContext(cacheId);

        finishUnmarshalCacheObject(failedKey, cctx, ldr);
        finishUnmarshalCacheObject(remapKey, cctx, ldr);

        finishUnmarshalCacheObjects(nearVals, cctx, ldr);

        if (ret != null)
            ret.finishUnmarshal(cctx, ldr);
    }



    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!super.writeTo(buf, writer))
            return false;

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 3:
                if (!writer.writeByteArray("errBytes", errBytes))
                    return false;

                writer.incrementState();

            case 4:
                if (!writer.writeMessage("failedKey", failedKey))
                    return false;

                writer.incrementState();

            case 5:
                if (!writer.writeMessage("futVer", futVer))
                    return false;

                writer.incrementState();

            case 6:
                if (!writer.writeMessage("nearExpireTimes", nearExpireTimes))
                    return false;

                writer.incrementState();

            case 7:
                if (!writer.writeCollection("nearSkipIdxs", nearSkipIdxs, MessageCollectionItemType.INT))
                    return false;

                writer.incrementState();

            case 8:
                if (!writer.writeMessage("nearTtls", nearTtls))
                    return false;

                writer.incrementState();

            case 9:
                if (!writer.writeCollection("nearVals", nearVals, MessageCollectionItemType.MSG))
                    return false;

                writer.incrementState();

            case 10:
                if (!writer.writeCollection("nearValsIdxs", nearValsIdxs, MessageCollectionItemType.INT))
                    return false;

                writer.incrementState();

            case 11:
                if (!writer.writeMessage("nearVer", nearVer))
                    return false;

                writer.incrementState();

            case 12:
                if (!writer.writeMessage("remapKey", remapKey))
                    return false;

                writer.incrementState();

            case 13:
                if (!writer.writeMessage("ret", ret))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        if (!super.readFrom(buf, reader))
            return false;

        switch (reader.state()) {
            case 3:
                errBytes = reader.readByteArray("errBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 4:
                failedKey = reader.readMessage("failedKey");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 5:
                futVer = reader.readMessage("futVer");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 6:
                nearExpireTimes = reader.readMessage("nearExpireTimes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 7:
                nearSkipIdxs = reader.readCollection("nearSkipIdxs", MessageCollectionItemType.INT);

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 8:
                nearTtls = reader.readMessage("nearTtls");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 9:
                nearVals = reader.readCollection("nearVals", MessageCollectionItemType.MSG);

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 10:
                nearValsIdxs = reader.readCollection("nearValsIdxs", MessageCollectionItemType.INT);

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 11:
                nearVer = reader.readMessage("nearVer");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 12:
                remapKey = reader.readMessage("remapKey");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 13:
                ret = reader.readMessage("ret");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(GridNearAtomicSingleUpdateResponse.class);
    }

    /** {@inheritDoc} */
    @Override public byte directType() {
        return 41;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 14;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridNearAtomicSingleUpdateResponse.class, this, "parent");
    }
}