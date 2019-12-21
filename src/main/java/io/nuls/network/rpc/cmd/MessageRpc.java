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
package io.nuls.network.rpc.cmd;

import io.nuls.core.RPCUtil;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.log.Log;
import io.nuls.core.rpc.model.*;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.network.constant.CmdConstant;
import io.nuls.network.constant.NetworkConstant;
import io.nuls.network.constant.NetworkErrorCode;
import io.nuls.network.manager.MessageManager;
import io.nuls.network.manager.NodeGroupManager;
import io.nuls.network.manager.handler.MessageHandlerFactory;
import io.nuls.network.model.Node;
import io.nuls.network.model.NodeGroup;
import io.nuls.network.model.message.base.MessageHeader;
import io.nuls.network.utils.LoggerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author lan
 * @description 消息远程调用
 * 模块消息处理器注册
 * 发送消息调用
 * @date 2018/11/12
 **/
@Component
public class MessageRpc {

    private MessageHandlerFactory messageHandlerFactory = MessageHandlerFactory.getInstance();

    @CmdAnnotation(cmd = CmdConstant.CMD_NW_PROTOCOL_REGISTER, version = 1.0,
            description = "模块协议指令注册")
    @Parameters(value = {
            @Parameter(parameterName = "role", requestType = @TypeDescriptor(value = String.class), parameterDes = "模块角色名称"),
            @Parameter(parameterName = "protocolCmds", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "注册指令列表")
    })
    @ResponseData(description = "无特定返回值，没有错误即成功")
    public Response protocolRegister(Map params) {
        String role = String.valueOf(params.get("role"));
        try {
            /*
             * 如果外部模块修改了调用注册信息，进行重启，则清理缓存信息，并重新注册
             * clear cache protocolRoleHandler
             */
            messageHandlerFactory.clearCacheProtocolRoleHandlerMap(role);
            List<String> protocolCmds = (List<String>) params.get("protocolCmds");
            for (String cmd : protocolCmds) {
                messageHandlerFactory.addProtocolRoleHandlerMap(cmd, CmdPriority.DEFAULT, role);
            }
            Log.info("----------------------------new message register---------------------------");
        } catch (Exception e) {
            LoggerUtil.COMMON_LOG.error(role, e);
            return failed(NetworkErrorCode.PARAMETER_ERROR);
        }
        return success();
    }

    @CmdAnnotation(cmd = CmdConstant.CMD_NW_PROTOCOL_PRIORITY_REGISTER, version = 1.0,
            description = "模块协议指令注册，带有优先级参数")
    @Parameters(value = {
            @Parameter(parameterName = "role", requestType = @TypeDescriptor(value = String.class), parameterDes = "模块角色名称"),
            @Parameter(parameterName = "protocolCmds", requestType = @TypeDescriptor(value = List.class, collectionElement = Map.class, mapKeys = {
                    @Key(name = "cmd", valueType = String.class, description = "协议指令名称,12byte"),
                    @Key(name = "priority", valueType = String.class, description = "优先级,3个等级,HIGH,DEFAULT,LOWER")
            }), parameterDes = "注册指令列表")
    })
    @ResponseData(description = "无特定返回值，没有错误即成功")
    public Response protocolRegisterWithPriority(Map params) {
        String role = String.valueOf(params.get("role"));
        /*
         * 如果外部模块修改了调用注册信息，进行重启，则清理缓存信息，并重新注册
         * clear cache protocolRoleHandler
         */
        messageHandlerFactory.clearCacheProtocolRoleHandlerMap(role);
        List<Map<String, Object>> protocolCmds = (List<Map<String, Object>>) params.get("protocolCmds");
        for (Map<String, Object> cmdMap : protocolCmds) {
            String cmd = (String) cmdMap.get("cmd");
            String priority = cmdMap.get("priority") == null ? "DEFAULT" : cmdMap.get("priority").toString();
            messageHandlerFactory.addProtocolRoleHandlerMap(cmd, CmdPriority.valueOf(priority), role);
        }
        return success();
    }

    /**
     * nw_broadcast
     * 外部广播接收
     */
    public boolean broadcast(int chainId, String excludeNodes, String messageBodyStr, String command, boolean isCross, int percent) throws IOException {
        byte[] messageBody = RPCUtil.decode(messageBodyStr);
        MessageManager messageManager = MessageManager.getInstance();
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        if (null == nodeGroup) {
            LoggerUtil.logger(chainId).error("chain is not exist!");
            return false;
        }
        long magicNumber = nodeGroup.getMagicNumber();
        long checksum = messageManager.getCheckSum(messageBody);
        MessageHeader header = new MessageHeader(command, magicNumber, checksum, messageBody.length);
        byte[] headerByte = header.serialize();
        byte[] message = new byte[headerByte.length + messageBody.length];
        System.arraycopy(headerByte, 0, message, 0, headerByte.length);
        System.arraycopy(messageBody, 0, message, headerByte.length, messageBody.length);
        Collection<Node> nodesCollection = nodeGroup.getAvailableNodes(isCross);
        excludeNodes = NetworkConstant.COMMA + excludeNodes + NetworkConstant.COMMA;
        List<Node> nodes = new ArrayList<>();
        for (Node node : nodesCollection) {
            if (!excludeNodes.contains(NetworkConstant.COMMA + node.getId() + NetworkConstant.COMMA)) {
                nodes.add(node);
            }
        }
        if (0 == nodes.size()) {
            return false;
        } else {
            messageManager.broadcastToNodes(message, command, nodes, true, percent);
        }
        return true;
    }

    /**
     * nw_sendPeersMsg
     */

    public void sendPeersMsg(int chainId, String nodes, String messageBodyStr, String command) throws IOException {
        byte[] messageBody = RPCUtil.decode(messageBodyStr);
        MessageManager messageManager = MessageManager.getInstance();
        NodeGroupManager nodeGroupManager = NodeGroupManager.getInstance();
        NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByChainId(chainId);
        long magicNumber = nodeGroup.getMagicNumber();
        long checksum = messageManager.getCheckSum(messageBody);
        MessageHeader header = new MessageHeader(command, magicNumber, checksum, messageBody.length);
        byte[] headerByte = header.serialize();
        byte[] message = new byte[headerByte.length + messageBody.length];
        System.arraycopy(headerByte, 0, message, 0, headerByte.length);
        System.arraycopy(messageBody, 0, message, headerByte.length, messageBody.length);
        String[] nodeIds = nodes.split(",");
        List<Node> nodesList = new ArrayList<>();
        for (String nodeId : nodeIds) {
            Node availableNode = nodeGroup.getAvailableNode(nodeId);
            if (null != availableNode) {
                nodesList.add(availableNode);
            } else {
                LoggerUtil.logger(chainId).error("cmd={},node = {} is not available!", command, nodeId);
            }
        }
        if (nodesList.size() > 0) {
            messageManager.broadcastToNodes(message, command, nodesList, true, NetworkConstant.FULL_BROADCAST_PERCENT);
        }
    }
}
