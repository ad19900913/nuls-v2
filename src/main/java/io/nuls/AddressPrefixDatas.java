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
package io.nuls;

import io.nuls.account.rpc.cmd.AccountResource;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.log.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地址前缀对象集合管理
 *
 * @author lanjinsheng
 * @date 2019-07-25
 */
@Component
public class AddressPrefixDatas {
    /**
     * chainId-地址映射表
     */
    private static Map<Integer, String> ADDRESS_PREFIX_MAP = new HashMap<Integer, String>();

    @Autowired
    private static AccountResource accountResource;

    public Map<Integer, String> getPrefixFromAccountModule() {
        try {
            List<Map<String, Object>> addressPrefixList = accountResource.getAllAddressPrefix();
            if (addressPrefixList.size() > 0) {
                for (Map<String, Object> addressPrefixMap : addressPrefixList) {
                    ADDRESS_PREFIX_MAP.put(Integer.valueOf(addressPrefixMap.get("chainId").toString()), String.valueOf(addressPrefixMap.get("addressPrefix")));
                }
            }
        } catch (Exception e) {
            Log.error(e);
        }
        return ADDRESS_PREFIX_MAP;
    }

    public Map<Integer, String> syncAddressPrefix() {
        return getPrefixFromAccountModule();
    }
}
