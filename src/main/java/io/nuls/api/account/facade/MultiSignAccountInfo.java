package io.nuls.api.account.facade;

import io.nuls.crosschain.base.data.Address;

import java.util.List;

/**
 * @Author: zhoulijun
 * @Time: 2019-07-23 17:05
 * @Description: 功能描述
 */
public class MultiSignAccountInfo {

    private int chainId;
    private Address address;
    private int minSigns;
    private List<String> pubKeyList;
    private String alias;

}
