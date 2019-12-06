package io.nuls.api.contract.facade;

import io.nuls.crosschain.base.api.provider.BaseReq;

/**
 * @Author: zhoulijun
 * @Time: 2019-03-23 16:13
 * @Description: 功能描述
 */
public class GetContractResultReq extends BaseReq {

    private String hash;

    public GetContractResultReq(String hash) {
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
