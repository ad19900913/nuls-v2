package io.nuls.api.contract.facade;

import io.nuls.crosschain.base.api.provider.BaseReq;

/**
 * @Author: zhoulijun
 * @Time: 2019-03-23 15:04
 * @Description: 功能描述
 */
public class GetContractConstructorArgsReq extends BaseReq {

    private String contractCode;

    public GetContractConstructorArgsReq(String contractCode) {
        this.contractCode = contractCode;
    }

    public String getContractCode() {
        return contractCode;
    }

    public void setContractCode(String contractCode) {
        this.contractCode = contractCode;
    }
}
