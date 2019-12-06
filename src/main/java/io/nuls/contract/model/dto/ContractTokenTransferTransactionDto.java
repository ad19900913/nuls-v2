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
package io.nuls.contract.model.dto;


import io.nuls.contract.model.po.ContractTokenTransferInfoPo;
import io.nuls.contract.util.ContractUtil;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.rpc.model.ApiModel;
import io.nuls.core.rpc.model.ApiModelProperty;

/**
 * @author: PierreLuo
 */
@ApiModel
public class ContractTokenTransferTransactionDto {

    @ApiModelProperty(description = "合约地址")
    private String contractAddress;
    @ApiModelProperty(description = "付款方")
    private String from;
    @ApiModelProperty(description = "收款方")
    private String to;
    @ApiModelProperty(description = "转账金额")
    private String value;
    @ApiModelProperty(description = "交易时间")
    private long time;
    @ApiModelProperty(description = "交易状态（0 - 确认中， 1 - 已确认， 2 - 失败）")
    private byte status;
    @ApiModelProperty(description = "交易hash")
    private String txHash;
    @ApiModelProperty(description = "区块高度")
    private long blockHeight;
    @ApiModelProperty(description = "token名称")
    private String name;
    @ApiModelProperty(description = "token符号")
    private String symbol;
    @ApiModelProperty(description = "token支持的小数位数")
    private long decimals;
    @ApiModelProperty(description = "token资产变动信息")
    private String info;

    public ContractTokenTransferTransactionDto(ContractTokenTransferInfoPo po, byte[] address) {
        this.contractAddress = po.getContractAddress();
        if (po.getFrom() != null) {
            this.from = AddressTool.getStringAddressByBytes(po.getFrom());
        }
        if (po.getTo() != null) {
            this.to = AddressTool.getStringAddressByBytes(po.getTo());
        }
        this.value = ContractUtil.bigInteger2String(po.getValue());
        this.time = po.getTime();
        this.status = po.getStatus();
        this.txHash = HexUtil.encode(po.getTxHash());
        this.blockHeight = po.getBlockHeight();
        this.name = po.getName();
        this.symbol = po.getSymbol();
        this.decimals = po.getDecimals();
        this.info = po.getInfo(address);
    }

    public int compareTo(long thatTime) {
        if (this.time > thatTime) {
            return -1;
        } else if (this.time < thatTime) {
            return 1;
        }
        return 0;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public byte getStatus() {
        return status;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getDecimals() {
        return decimals;
    }

    public void setDecimals(long decimals) {
        this.decimals = decimals;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}
