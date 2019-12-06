package io.nuls.crosschain.base.message;

import io.nuls.core.basic.NulsByteBuffer;
import io.nuls.core.basic.NulsOutputStreamBuffer;
import io.nuls.core.data.NulsHash;
import io.nuls.core.exception.NulsException;
import io.nuls.crosschain.base.message.base.BaseMessage;

import java.io.IOException;

/**
 * 接收跨链交易在主网节点的处理结果
 *
 * @author tag
 * @date 2019/4/4
 */
public class GetCtxStateMessage extends BaseMessage {
    /**
     * 请求连协议跨链交易Hash
     */
    private NulsHash requestHash;


    @Override
    protected void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.write(requestHash.getBytes());
    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) throws NulsException {
        this.requestHash = byteBuffer.readHash();
    }

    @Override
    public int size() {
        int size = 0;
        size += NulsHash.HASH_LENGTH;
        return size;
    }

    public NulsHash getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(NulsHash requestHash) {
        this.requestHash = requestHash;
    }
}
