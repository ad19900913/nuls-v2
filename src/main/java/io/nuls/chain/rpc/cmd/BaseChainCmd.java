/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.chain.rpc.cmd;

import io.nuls.chain.info.CmConstants;
import io.nuls.chain.info.CmErrorCode;
import io.nuls.chain.model.dto.AccountBalance;
import io.nuls.chain.model.po.Asset;
import io.nuls.chain.util.TxUtil;
import io.nuls.core.basic.TransactionFeeCalculator;
import io.nuls.core.data.CoinData;
import io.nuls.core.data.CoinFrom;
import io.nuls.core.data.CoinTo;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.model.BigIntegerUtils;
import io.nuls.core.signture.P2PHKSignature;

import java.math.BigInteger;

/**
 * @author lan
 * @date 2018/11/28
 */
public class BaseChainCmd {

    /**
     * 注册链或资产封装coinData,x%资产进入黑洞，y%资产进入锁定
     */
    CoinData getRegCoinData(Asset asset, int nulsChainId, int nulsAssetId, int txSize, AccountBalance accountBalance) throws NulsRuntimeException {
        txSize = txSize + P2PHKSignature.SERIALIZE_LENGTH;
        CoinData coinData = new CoinData();
        BigInteger lockAmount = asset.getDepositNuls().subtract(asset.getDestroyNuls());
        CoinTo to1 = new CoinTo(asset.getAddress(), nulsChainId, nulsAssetId, lockAmount, -1);
        CoinTo to2 = new CoinTo(CmConstants.BLACK_HOLE_ADDRESS, nulsChainId, nulsAssetId, asset.getDestroyNuls(), 0);
        coinData.addTo(to1);
        coinData.addTo(to2);
        //手续费
        CoinFrom from = new CoinFrom(asset.getAddress(), nulsChainId, nulsAssetId, asset.getDepositNuls(), accountBalance.getNonce(), (byte) 0);
        coinData.addFrom(from);
        txSize += to1.size();
        txSize += to2.size();
        txSize += from.size();
        BigInteger fee = TransactionFeeCalculator.getNormalTxFee(txSize);
        String fromAmount = BigIntegerUtils.bigIntegerToString(asset.getDepositNuls().add(fee));
        if (BigIntegerUtils.isLessThan(accountBalance.getAvailable(), fromAmount)) {
            throw new NulsRuntimeException(CmErrorCode.BALANCE_NOT_ENOUGH);
        }
        from.setAmount(BigIntegerUtils.stringToBigInteger(fromAmount));
        return coinData;
    }

    /**
     * 注销资产进行处理
     */
    CoinData getDisableCoinData(Asset asset, int nulsChainId, int nulsAssetId, int txSize) throws NulsRuntimeException {
        txSize = txSize + P2PHKSignature.SERIALIZE_LENGTH;

        BigInteger lockAmount = asset.getDepositNuls().subtract(asset.getDestroyNuls());
        CoinTo to = new CoinTo(asset.getAddress(), nulsChainId, nulsAssetId, lockAmount, 0);
        CoinData coinData = new CoinData();
        coinData.addTo(to);
        //手续费
        CoinFrom from = new CoinFrom(asset.getAddress(), nulsChainId, nulsAssetId, lockAmount, TxUtil.getNonceByTxHash(asset.getTxHash()), (byte) -1);
        coinData.addFrom(from);
        txSize += to.size();
        txSize += from.size();
        BigInteger fee = TransactionFeeCalculator.getNormalTxFee(txSize);
        //手续费从抵押里扣除
        to.setAmount(lockAmount.subtract(fee));
        return coinData;
    }

}
