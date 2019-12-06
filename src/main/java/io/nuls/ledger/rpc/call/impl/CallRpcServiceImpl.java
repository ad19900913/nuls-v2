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
package io.nuls.ledger.rpc.call.impl;

import io.nuls.core.core.annotation.Component;
import io.nuls.core.parse.JSONUtils;
import io.nuls.core.rpc.model.ModuleE;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.core.rpc.netty.processor.ResponseMessageProcessor;
import io.nuls.ledger.constant.CmdConstant;
import io.nuls.ledger.rpc.call.CallRpcService;
import io.nuls.ledger.utils.LoggerUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author lanjinsheng
 * @description
 * @date 2019/06/05
 */
@Component
public class CallRpcServiceImpl implements CallRpcService {
    @Override
    public long getBlockLatestHeight(int chainId) {
        Map<String, Object> map = new HashMap<>();
        map.put("chainId", chainId);
        try {
            Response response = ResponseMessageProcessor.requestAndResponse(ModuleE.BL.abbr, CmdConstant.CMD_LATEST_HEIGHT, map);
            if (null != response && response.isSuccess()) {
                Map responseData = (Map) response.getResponseData();
                if (null != responseData) {
                    Map datas = (Map) responseData.get(CmdConstant.CMD_LATEST_HEIGHT);
                    if (null != datas) {
                        return Long.parseLong(datas.get("value").toString());
                    }
                }
            } else {
                LoggerUtil.logger(chainId).error("getBlockLatestHeight fail.response={}", JSONUtils.obj2json(response));
            }
        } catch (Exception e) {
            LoggerUtil.logger(chainId).error(e);
        }
        return 0;
    }
}
