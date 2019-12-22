/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2019 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.transaction.rpc.cmd;

import io.nuls.core.RPCUtil;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.NulsHash;
import io.nuls.core.data.Transaction;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.transaction.constant.TxConstant;
import io.nuls.transaction.constant.TxErrorCode;
import io.nuls.transaction.manager.ChainManager;
import io.nuls.transaction.model.bo.Chain;
import io.nuls.transaction.model.bo.VerifyLedgerResult;
import io.nuls.transaction.model.bo.VerifyResult;
import io.nuls.transaction.model.po.TransactionConfirmedPO;
import io.nuls.transaction.rpc.call.LedgerCall;
import io.nuls.transaction.service.ConfirmedTxService;
import io.nuls.transaction.service.TxService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.nuls.transaction.utils.LoggerUtil.LOG;

/**
 * @author: Charlie
 * @date: 2019/3/12
 */
@Component
public class ClientCmd {

    @Autowired
    private TxService txService;

    @Autowired
    private ConfirmedTxService confirmedTxService;

    @Autowired
    private ChainManager chainManager;

    public Map<String, Object> getTx(int chainId, String txHash) throws NulsException, IOException {
        Chain chain = chainManager.getChain(chainId);
        if (!NulsHash.validHash(txHash)) {
            throw new NulsException(TxErrorCode.HASH_ERROR);
        }
        TransactionConfirmedPO tx = txService.getTransaction(chain, NulsHash.fromHex(txHash));
        Map<String, Object> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_4);
        if (tx == null) {
            resultMap.put("tx", null);
        } else {
            resultMap.put("tx", RPCUtil.encode(tx.getTx().serialize()));
            resultMap.put("height", tx.getBlockHeight());
            resultMap.put("status", tx.getStatus());
        }
        return resultMap;
    }

    public Map<String, Object> getConfirmedTx(int chainId, String txHash) throws NulsException, IOException {
        Chain chain = chainManager.getChain(chainId);
            if (!NulsHash.validHash(txHash)) {
                throw new NulsException(TxErrorCode.HASH_ERROR);
            }
            TransactionConfirmedPO tx = confirmedTxService.getConfirmedTransaction(chain, NulsHash.fromHex(txHash));
            Map<String, Object> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_4);
            if (tx == null) {
                LOG.debug("getConfirmedTransaction fail, tx is null. txHash:{}", txHash);
                resultMap.put("tx", null);
            } else {
                LOG.debug("getConfirmedTransaction success. txHash:{}", txHash);
                resultMap.put("tx", RPCUtil.encode(tx.getTx().serialize()));
                resultMap.put("height", tx.getBlockHeight());
                resultMap.put("status", tx.getStatus());
            }
            return resultMap;
    }


    public String verifyTx(int chainId, Transaction tx) throws IOException {
        Chain chain = chainManager.getChain(chainId);
        VerifyResult verifyResult = txService.verify(chain, tx, true);
        if (!verifyResult.getResult()) {
            throw new NulsRuntimeException(verifyResult.getErrorCode());
        }
        VerifyLedgerResult verifyLedgerResult = LedgerCall.verifyCoinData(chain, RPCUtil.encode(tx.serialize()));
        if (!verifyLedgerResult.getSuccess()) {
            throw new NulsRuntimeException(verifyLedgerResult.getErrorCode());
        }
        return tx.getHash().toHex();
    }

    private void errorLogProcess(Chain chain, Exception e) {
        if (chain == null) {
            LOG.error(e);
        } else {
            chain.getLogger().error(e);
        }
    }

}
