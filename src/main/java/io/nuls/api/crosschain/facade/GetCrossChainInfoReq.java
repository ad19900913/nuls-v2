package io.nuls.api.crosschain.facade;

import io.nuls.crosschain.base.api.provider.BaseReq;

/**
 * @Author: zhoulijun
 * @Time: 2019-05-07 11:49
 * @Description: 功能描述
 */
public class GetCrossChainInfoReq extends BaseReq {

    public GetCrossChainInfoReq(Integer chainId) {
        this.setChainId(chainId);
    }

}
