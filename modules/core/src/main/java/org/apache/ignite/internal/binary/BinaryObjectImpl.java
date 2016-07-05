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

package org.apache.ignite.internal.binary;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryType;
import org.apache.ignite.internal.GridDirectTransient;
import org.apache.ignite.internal.IgniteCodeGeneratingFail;
import org.apache.ignite.internal.binary.streams.BinaryHeapInputStream;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheObjectContext;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.binary.CacheObjectBinaryProcessorImpl;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;
import org.jetbrains.annotations.Nullable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.*;

/**
 * Binary object implementation.
 */
@IgniteCodeGeneratingFail // Fields arr and start should not be generated by MessageCodeGenerator.
public final class BinaryObjectImpl extends BinaryObjectExImpl implements Externalizable, KeyCacheObject {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    @GridDirectTransient
    private BinaryContext ctx;

    /** */
    private byte[] arr;

    /** */
    private int start;

    /** */
    @GridDirectTransient
    private Object obj;

    /** */
    @GridDirectTransient
    private boolean detachAllowed;

    /**
     * For {@link Externalizable}.
     */
    public BinaryObjectImpl() {
        // No-op.
    }

    /**
     * @param ctx Context.
     * @param arr Array.
     * @param start Start.
     */
    public BinaryObjectImpl(BinaryContext ctx, byte[] arr, int start) {
        assert ctx != null;
        assert arr != null;

        this.ctx = ctx;
        this.arr = arr;
        this.start = start;
    }

    /** {@inheritDoc} */
    @Override public byte cacheObjectType() {
        return TYPE_BINARY;
    }

    /** {@inheritDoc} */
    @Override public boolean isPlatformType() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean internal() {
        return false;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public <T> T value(CacheObjectContext ctx, boolean cpy) {
        Object obj0 = obj;

        if (obj0 == null || (cpy && needCopy(ctx)))
            obj0 = deserializeValue(ctx);

        return (T)obj0;
    }

    /** {@inheritDoc} */
    @Override public byte[] valueBytes(CacheObjectContext ctx) throws IgniteCheckedException {
        if (detached())
            return array();

        int len = length();

        byte[] arr0 = new byte[len];

        U.arrayCopy(arr, start, arr0, 0, len);

        return arr0;
    }

    /** {@inheritDoc} */
    @Override public CacheObject prepareForCache(CacheObjectContext ctx) {
        if (detached())
            return this;

        return (BinaryObjectImpl)detach();
    }

    /** {@inheritDoc} */
    @Override public void finishUnmarshal(CacheObjectContext ctx, ClassLoader ldr) throws IgniteCheckedException {
        this.ctx = ((CacheObjectBinaryProcessorImpl)ctx.processor()).binaryContext();
    }

    /** {@inheritDoc} */
    @Override public void prepareMarshal(CacheObjectContext ctx) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public int length() {
        return BinaryPrimitives.readInt(arr, start + GridBinaryMarshaller.TOTAL_LEN_POS);
    }

    /**
     * @return Detached binary object.
     */
    public BinaryObject detach() {
        if (!detachAllowed || detached())
            return this;

        int len = length();

        byte[] arr0 = new byte[len];

        U.arrayCopy(arr, start, arr0, 0, len);

        return new BinaryObjectImpl(ctx, arr0, 0);
    }

    /**
     * @return Detached or not.
     */
    public boolean detached() {
        return start == 0 && length() == arr.length;
    }

    /**
     * @param detachAllowed Detach allowed flag.
     */
    public void detachAllowed(boolean detachAllowed) {
        this.detachAllowed = detachAllowed;
    }

    /**
     * @return Context.
     */
    public BinaryContext context() {
        return ctx;
    }

    /**
     * @param ctx Context.
     */
    public void context(BinaryContext ctx) {
        this.ctx = ctx;
    }

    /** {@inheritDoc} */
    @Override public byte[] array() {
        return arr;
    }

    /** {@inheritDoc} */
    @Override public int start() {
        return start;
    }

    /** {@inheritDoc} */
    @Override public long offheapAddress() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override protected boolean hasArray() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public int typeId() {
        return BinaryPrimitives.readInt(arr, start + GridBinaryMarshaller.TYPE_ID_POS);
    }

    /** {@inheritDoc} */
    @Nullable @Override public BinaryType type() throws BinaryObjectException {
        return BinaryUtils.typeProxy(ctx, this);
    }

    /** {@inheritDoc} */
    @Nullable @Override public BinaryType rawType() throws BinaryObjectException {
        return BinaryUtils.type(ctx, this);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public <F> F field(String fieldName) throws BinaryObjectException {
        return (F) reader(null).unmarshalField(fieldName);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public <F> F field(int fieldId) throws BinaryObjectException {
        return (F) reader(null).unmarshalField(fieldId);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override protected <F> F fieldByOrder(int order) {
        Object val;

        // Calculate field position.
        int schemaOffset = BinaryPrimitives.readInt(arr, start + GridBinaryMarshaller.SCHEMA_OR_RAW_OFF_POS);

        short flags = BinaryPrimitives.readShort(arr, start + GridBinaryMarshaller.FLAGS_POS);

        int fieldIdLen = BinaryUtils.isCompactFooter(flags) ? 0 : BinaryUtils.FIELD_ID_LEN;
        int fieldOffsetLen = BinaryUtils.fieldOffsetLength(flags);

        int fieldOffsetPos = start + schemaOffset + order * (fieldIdLen + fieldOffsetLen) + fieldIdLen;

        int fieldPos;

        if (fieldOffsetLen == BinaryUtils.OFFSET_1)
            fieldPos = start + ((int)BinaryPrimitives.readByte(arr, fieldOffsetPos) & 0xFF);
        else if (fieldOffsetLen == BinaryUtils.OFFSET_2)
            fieldPos = start + ((int)BinaryPrimitives.readShort(arr, fieldOffsetPos) & 0xFFFF);
        else
            fieldPos = start + BinaryPrimitives.readInt(arr, fieldOffsetPos);

        // Read header and try performing fast lookup for well-known types (the most common types go first).
        byte hdr = BinaryPrimitives.readByte(arr, fieldPos);

        switch (hdr) {
            case GridBinaryMarshaller.INT:
                val = BinaryPrimitives.readInt(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.LONG:
                val = BinaryPrimitives.readLong(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.BOOLEAN:
                val = BinaryPrimitives.readBoolean(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.SHORT:
                val = BinaryPrimitives.readShort(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.BYTE:
                val = BinaryPrimitives.readByte(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.CHAR:
                val = BinaryPrimitives.readChar(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.FLOAT:
                val = BinaryPrimitives.readFloat(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.DOUBLE:
                val = BinaryPrimitives.readDouble(arr, fieldPos + 1);

                break;

            case GridBinaryMarshaller.STRING: {
                int dataLen = BinaryPrimitives.readInt(arr, fieldPos + 1);

                val = new String(arr, fieldPos + 5, dataLen, UTF_8);

                break;
            }

            case GridBinaryMarshaller.DATE: {
                long time = BinaryPrimitives.readLong(arr, fieldPos + 1);

                val = new Date(time);

                break;
            }

            case GridBinaryMarshaller.TIMESTAMP: {
                long time = BinaryPrimitives.readLong(arr, fieldPos + 1);
                int nanos = BinaryPrimitives.readInt(arr, fieldPos + 1 + 8);

                Timestamp ts = new Timestamp(time);

                ts.setNanos(ts.getNanos() + nanos);

                val = ts;

                break;
            }

            case GridBinaryMarshaller.UUID: {
                long most = BinaryPrimitives.readLong(arr, fieldPos + 1);
                long least = BinaryPrimitives.readLong(arr, fieldPos + 1 + 8);

                val = new UUID(most, least);

                break;
            }

            case GridBinaryMarshaller.DECIMAL: {
                int scale = BinaryPrimitives.readInt(arr, fieldPos + 1);

                int dataLen = BinaryPrimitives.readInt(arr, fieldPos + 5);
                byte[] data = BinaryPrimitives.readByteArray(arr, fieldPos + 9, dataLen);

                BigInteger intVal = new BigInteger(data);

                if (scale < 0) {
                    scale &= 0x7FFFFFFF;

                    intVal = intVal.negate();
                }

                val = new BigDecimal(intVal, scale);

                break;
            }

            case GridBinaryMarshaller.NULL:
                val = null;

                break;

            default:
                val = BinaryUtils.unmarshal(BinaryHeapInputStream.create(arr, fieldPos), ctx, null);

                break;
        }

        return (F)val;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override protected <F> F field(BinaryReaderHandles rCtx, String fieldName) {
        return (F)reader(rCtx).unmarshalField(fieldName);
    }

    /** {@inheritDoc} */
    @Override public boolean hasField(String fieldName) {
        return reader(null).findFieldByName(fieldName);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Nullable @Override public <T> T deserialize() throws BinaryObjectException {
        Object obj0 = obj;

        if (obj0 == null)
            obj0 = deserializeValue(null);

        return (T)obj0;
    }

    /** {@inheritDoc} */
    @Override public BinaryObject clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return BinaryPrimitives.readInt(arr, start + GridBinaryMarshaller.HASH_CODE_POS);
    }

    /** {@inheritDoc} */
    @Override protected int schemaId() {
        return BinaryPrimitives.readInt(arr, start + GridBinaryMarshaller.SCHEMA_ID_POS);
    }

    /** {@inheritDoc} */
    @Override protected BinarySchema createSchema() {
        return reader(null).getOrCreateSchema();
    }

    /** {@inheritDoc} */
    @Override public void onAckReceived() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        if (detachAllowed) {
            int len = length();

            out.writeInt(len);
            out.write(arr, start, len);
            out.writeInt(0);
        }
        else {
            out.writeInt(arr.length);
            out.write(arr);
            out.writeInt(start);
        }
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ctx = GridBinaryMarshaller.threadLocalContext();

        arr = new byte[in.readInt()];

        in.readFully(arr);

        start = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 0:
                if (!writer.writeByteArray("arr",
                    arr,
                    detachAllowed ? start : 0,
                    detachAllowed ? length() : arr.length))
                    return false;

                writer.incrementState();

            case 1:
                if (!writer.writeInt("start", detachAllowed ? 0 : start))
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

        switch (reader.state()) {
            case 0:
                arr = reader.readByteArray("arr");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 1:
                start = reader.readInt("start");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public byte directType() {
        return 113;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 3;
    }

    /**
     * Runs value deserialization regardless of whether obj already has the deserialized value.
     * Will set obj if descriptor is configured to keep deserialized values.
     * @param coCtx CacheObjectContext.
     * @return Object.
     */
    private Object deserializeValue(@Nullable CacheObjectContext coCtx) {
        BinaryReaderExImpl reader = reader(null,
            coCtx != null ? coCtx.kernalContext().config().getClassLoader() : ctx.configuration().getClassLoader());

        Object obj0 = reader.deserialize();

        BinaryClassDescriptor desc = reader.descriptor();

        assert desc != null;

        if (coCtx != null && coCtx.storeValue())
            obj = obj0;

        return obj0;
    }

    /**
     * @param ctx Context.
     * @return {@code True} need to copy value returned to user.
     */
    private boolean needCopy(CacheObjectContext ctx) {
        return ctx.copyOnGet() && obj != null && !ctx.processor().immutable(obj);
    }

    /**
     * Create new reader for this object.
     *
     * @param rCtx Reader context.
     * @return Reader.
     */
    private BinaryReaderExImpl reader(@Nullable BinaryReaderHandles rCtx, @Nullable ClassLoader ldr) {
        if (ldr == null)
            ldr = ctx.configuration().getClassLoader();

        return new BinaryReaderExImpl(ctx,
            BinaryHeapInputStream.create(arr, start),
            ldr,
            rCtx);
    }

    /**
     * Create new reader for this object.
     *
     * @param rCtx Reader context.
     * @return Reader.
     */
    private BinaryReaderExImpl reader(@Nullable BinaryReaderHandles rCtx) {
        return reader(rCtx, null);
    }
}
