package io.nuls.api.contract.facade;

import io.nuls.crosschain.base.api.provider.BaseReq;

/**
 * @Author: zhoulijun
 * @Time: 2019-03-23 16:55
 * @Description: 功能描述
 */
public class GetAccountContractListReq extends BaseReq {

    private String address;

    public GetAccountContractListReq(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
