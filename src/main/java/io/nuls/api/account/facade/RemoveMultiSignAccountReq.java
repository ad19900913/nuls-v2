package io.nuls.api.account.facade;

import io.nuls.crosschain.base.api.provider.BaseReq;

/**
 * @Author: zhoulijun
 * @Time: 2019-07-19 16:18
 * @Description: 删除多签账户
 */
public class RemoveMultiSignAccountReq extends BaseReq {

    private String address;

    public RemoveMultiSignAccountReq(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
