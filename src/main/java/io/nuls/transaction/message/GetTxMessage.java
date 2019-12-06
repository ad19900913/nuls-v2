package io.nuls.transaction.message;

import io.nuls.core.basic.NulsByteBuffer;
import io.nuls.core.basic.NulsOutputStreamBuffer;
import io.nuls.core.data.NulsHash;
import io.nuls.core.exception.NulsException;
import io.nuls.crosschain.base.data.BaseBusinessMessage;

import java.io.IOException;

/**
 * 获取完整交易数据
 *
 * @author: qinyifeng
 * @date: 2018/12/26
 */
public class GetTxMessage extends BaseBusinessMessage {
    /**
     * 交易hash
     */
    private NulsHash txHash;

    public NulsHash getTxHash() {
        return txHash;
    }

    public void setTxHash(NulsHash txHash) {
        this.txHash = txHash;
    }

    @Override
    public int size() {
        int size = 0;
        size += NulsHash.HASH_LENGTH;
        return size;
    }

    @Override
    public void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.write(txHash.getBytes());
    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) throws NulsException {
        this.txHash = byteBuffer.readHash();
    }
}
