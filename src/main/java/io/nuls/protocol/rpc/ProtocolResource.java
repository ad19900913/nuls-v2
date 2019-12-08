/*
 * MIT License
 * Copyright (c) 2017-2019 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.protocol.rpc;

import com.google.common.collect.Maps;
import io.nuls.core.basic.ProtocolVersion;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.data.BlockExtendsData;
import io.nuls.core.data.BlockHeader;
import io.nuls.core.log.logback.NulsLogger;
import io.nuls.protocol.Protocol;
import io.nuls.protocol.manager.ContextManager;
import io.nuls.protocol.model.ProtocolContext;
import io.nuls.protocol.service.ProtocolService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块的对外接口类
 *
 * @author captain
 * @version 1.0
 * @date 18-11-9 下午2:04
 */
@Component
public class ProtocolResource {
    @Autowired
    private ProtocolService service;

    /**
     * 获取当前主网版本信息
     *
     * @return
     */
    public Map<String, ProtocolVersion> getVersion(int chainId) {
        ProtocolContext context = ContextManager.getContext(chainId);
        ProtocolVersion currentProtocolVersion = context.getCurrentProtocolVersion();
        ProtocolVersion localProtocolVersion = context.getLocalProtocolVersion();
        Map<String, ProtocolVersion> result = new HashMap<>();
        result.put("currentProtocolVersion", currentProtocolVersion);
        result.put("localProtocolVersion", localProtocolVersion);
        return result;
    }

    /**
     * 验证新收到区块的版本号是否正确
     *
     * @return
     */
    public boolean checkBlockVersion(int chainId, BlockExtendsData extendsData) {
        ProtocolContext context = ContextManager.getContext(chainId);
        ProtocolVersion currentProtocol = context.getCurrentProtocolVersion();
        //收到的新区块和本地主网版本不一致，验证不通过
        if (currentProtocol.getVersion() != extendsData.getMainVersion()) {
            NulsLogger logger = context.getLogger();
            logger.info("------block version error, mainVersion:" + currentProtocol.getVersion() + ",blockVersion:" + extendsData.getMainVersion());
            return false;
        }
        return true;
    }

    /**
     * 保存区块
     *
     * @return
     */
    public boolean save(int chainId, BlockHeader blockHeader) {
        return service.save(chainId, blockHeader);
    }

    /**
     * 回滚区块
     *
     * @return
     */
    public boolean rollback(int chainId, BlockHeader blockHeader) {
        return service.rollback(chainId, blockHeader);
    }

    /**
     * 接受各模块注册多版本配置
     *
     * @return
     */
    public boolean registerProtocol(int chainId, String moduleCode, List<Protocol> list) {
        ProtocolContext context = ContextManager.getContext(chainId);
        Map<Short, List<Map.Entry<String, Protocol>>> protocolMap = context.getProtocolMap();
        NulsLogger logger = context.getLogger();
        logger.info("--------------------registerProtocol---------------------------");
        logger.info("moduleCode-" + moduleCode);
        for (Protocol protocol : list) {
            short version = protocol.getVersion();
            List<Map.Entry<String, Protocol>> protocolList = protocolMap.computeIfAbsent(version, k -> new ArrayList<>());
            protocolList.add(Maps.immutableEntry(moduleCode, protocol));
            logger.info("protocol-" + protocol);
        }
        return true;
    }

}
