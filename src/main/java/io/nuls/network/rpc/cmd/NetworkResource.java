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
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.log.Log;
import io.nuls.core.model.StringUtils;
import io.nuls.network.cfg.NetworkConfig;
import io.nuls.network.constant.NetworkConstant;
import io.nuls.network.constant.NodeConnectStatusEnum;
import io.nuls.network.manager.MessageManager;
import io.nuls.network.manager.NodeGroupManager;
import io.nuls.network.manager.StorageManager;
import io.nuls.network.manager.TimeManager;
import io.nuls.network.manager.handler.MessageHandlerFactory;
import io.nuls.network.model.Node;
import io.nuls.network.model.NodeGroup;
import io.nuls.network.model.message.base.MessageHeader;
import io.nuls.network.model.po.GroupPo;
import io.nuls.network.model.vo.NodeGroupVo;
import io.nuls.network.model.vo.NodeVo;
import io.nuls.network.netty.container.NodesContainer;
import io.nuls.network.rpc.CmdPriority;
import io.nuls.network.rpc.call.BlockRpcService;
import io.nuls.network.rpc.call.impl.BlockRpcServiceImpl;
import io.nuls.network.utils.IpUtil;
import io.nuls.network.utils.LoggerUtil;

import java.io.IOException;
import java.util.*;

/**
 * @author lan
 * @description
 * @date 2018/12/05
 **/
@Component
public class NetworkResource {
    private NodeGroupManager nodeGroupManager = NodeGroupManager.getInstance();
    private static final int STATE_ALL = 0;
    private static final int STATE_CONNECT = 1;
    private static final int STATE_DIS_CONNECT = 2;
    @Autowired
    NetworkConfig networkConfig;

    /**
     * nw_addNodes
     * 增加节点
     */
    public void addNodes(int chainId, int isCross, String nodes) {
        boolean blCross = false;
        if (1 == isCross) {
            blCross = true;
        }
        String[] peers = nodes.split(",");
        NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByChainId(chainId);
        for (String peer : peers) {
            String[] ipPort = IpUtil.changeHostToIp(peer);
            if (null == ipPort) {
                continue;
            }
            if (blCross) {
                nodeGroup.addNeedCheckNode(ipPort[0], Integer.parseInt(ipPort[1]), Integer.parseInt(ipPort[1]), blCross);
            } else {
                nodeGroup.addNeedCheckNode(ipPort[0], Integer.parseInt(ipPort[1]), 0, blCross);
            }
        }
    }


    /**
     * nw_delNodes
     * 删除节点
     */
    public void delNodes(int chainId, String nodes) {
        String[] peers = nodes.split(",");
        NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByChainId(chainId);
        for (String nodeId : peers) {
            nodeId = IpUtil.changeHostToIpStr(nodeId);
            if (null == nodeId) {
                continue;
            }
            //移除 peer
            Node node = nodeGroup.getLocalNetNodeContainer().getConnectedNodes().get(nodeId);
            if (null != node) {
                node.close();
            } else {
                nodeGroup.getLocalNetNodeContainer().getCanConnectNodes().remove(nodeId);
                nodeGroup.getLocalNetNodeContainer().getUncheckNodes().remove(nodeId);
                nodeGroup.getLocalNetNodeContainer().getDisconnectNodes().remove(nodeId);
                nodeGroup.getLocalNetNodeContainer().getFailNodes().remove(nodeId);
            }

            node = nodeGroup.getCrossNodeContainer().getConnectedNodes().get(nodeId);
            if (null != node) {
                node.close();
            } else {
                nodeGroup.getCrossNodeContainer().getCanConnectNodes().remove(nodeId);
                nodeGroup.getCrossNodeContainer().getUncheckNodes().remove(nodeId);
                nodeGroup.getCrossNodeContainer().getDisconnectNodes().remove(nodeId);
                nodeGroup.getCrossNodeContainer().getFailNodes().remove(nodeId);
            }

        }
    }

    public List<NodeVo> getNodes(int chainId, int state, boolean isCross, int startPage, int pageSize) {
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        List<Node> nodes = new ArrayList<>();
        if (isCross) {
            /*
             * 跨链连接
             * cross connection
             */
            addNode(nodes, state, nodeGroup.getCrossNodeContainer());
        } else {
            /*
             * 普通连接
             * comment connection
             */
            addNode(nodes, state, nodeGroup.getLocalNetNodeContainer());
        }
        int total = nodes.size();
        List<NodeVo> pageList = new ArrayList<>();
        if (0 == startPage && 0 == pageSize) {
            //get all datas
            for (Node node : nodes) {
                pageList.add(buildNodeVo(node, nodeGroup.getMagicNumber(), chainId));
            }
        } else {
            //get by page
            int currIdx = (startPage > 1 ? (startPage - 1) * pageSize : 0);
            for (int i = 0; i < pageSize && i < (total - currIdx); i++) {
                Node node = nodes.get(currIdx + i);
                NodeVo nodeVo = buildNodeVo(node, nodeGroup.getMagicNumber(), chainId);
                pageList.add(nodeVo);
            }
        }
        return pageList;
    }

    private void addNode(List<Node> nodes, int state, NodesContainer nodesContainer) {
        if (STATE_ALL == state) {
            /*
             * all connection
             */
            nodes.addAll(nodesContainer.getConnectedNodes().values());
            nodes.addAll(nodesContainer.getCanConnectNodes().values());
            nodes.addAll(nodesContainer.getDisconnectNodes().values());
            nodes.addAll(nodesContainer.getUncheckNodes().values());
            nodes.addAll(nodesContainer.getFailNodes().values());
        } else if (STATE_CONNECT == state) {
            /*
             * only  connection
             */
            nodes.addAll(nodesContainer.getAvailableNodes());
        } else if (STATE_DIS_CONNECT == state) {
            /*
             * only dis connection
             */
            nodes.addAll(nodesContainer.getCanConnectNodes().values());
            nodes.addAll(nodesContainer.getDisconnectNodes().values());
            nodes.addAll(nodesContainer.getUncheckNodes().values());
            nodes.addAll(nodesContainer.getFailNodes().values());
        }
    }

    /**
     * nw_updateNodeInfo
     * 更新区块高度与hash
     */
    public void updateNodeInfo(int chainId, String nodeId, long blockHeight, String blockHash) {
        NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByChainId(chainId);
        Node node = nodeGroup.getConnectedNode(nodeId);
        node.setBlockHash(blockHash);
        node.setBlockHeight(blockHeight);
    }


    private NodeVo buildNodeVo(Node node, long magicNumber, int chainId) {
        NodeVo nodeVo = new NodeVo();
        nodeVo.setBlockHash(node.getBlockHash());
        nodeVo.setBlockHeight(node.getBlockHeight());
        nodeVo.setState(node.getConnectStatus() == NodeConnectStatusEnum.AVAILABLE ? 1 : 0);
        nodeVo.setTime(node.getConnectTime());
        nodeVo.setChainId(chainId);
        nodeVo.setIp(node.getIp());
        nodeVo.setIsOut(node.getType() == Node.OUT ? 1 : 0);
        nodeVo.setMagicNumber(magicNumber);
        nodeVo.setNodeId(node.getId());
        nodeVo.setPort(node.getRemotePort());
        return nodeVo;
    }

    private MessageHandlerFactory messageHandlerFactory = MessageHandlerFactory.getInstance();

    public void protocolRegister(String role, List<String> protocolCmds) {
        /*
         * 如果外部模块修改了调用注册信息，进行重启，则清理缓存信息，并重新注册
         * clear cache protocolRoleHandler
         */
        messageHandlerFactory.clearCacheProtocolRoleHandlerMap(role);
        for (String cmd : protocolCmds) {
            messageHandlerFactory.addProtocolRoleHandlerMap(cmd, CmdPriority.DEFAULT, role);
        }
        Log.info("----------------------------new message register---------------------------");
    }

    public void protocolRegisterWithPriority(String role, List<String> protocolCmds, String priority) {
        /*
         * 如果外部模块修改了调用注册信息，进行重启，则清理缓存信息，并重新注册
         * clear cache protocolRoleHandler
         */
        messageHandlerFactory.clearCacheProtocolRoleHandlerMap(role);
        for (String cmd : protocolCmds) {
            messageHandlerFactory.addProtocolRoleHandlerMap(cmd, CmdPriority.valueOf(priority), role);
        }
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

    /**
     * nw_createNodeGroup
     * 主网创建跨链网络或者链工厂创建链
     */

    public void createNodeGroup(int chainId, long magicNumber, int maxOut, int maxIn, int minAvailableCount, boolean isCrossGroup) {
        List<GroupPo> nodeGroupPos = new ArrayList<>();
        if (maxOut == 0) {
            if (networkConfig.isMoonNode()) {
                maxOut = networkConfig.getCrossMaxOutCount();
            } else {
                maxOut = networkConfig.getMaxOutCount();
            }
        }
        if (maxIn == 0) {
            if (networkConfig.isMoonNode()) {
                maxIn = networkConfig.getCrossMaxInCount();
            } else {
                maxIn = networkConfig.getMaxInCount();
            }
        }
        if (!networkConfig.isMoonNode() && isCrossGroup) {
            LoggerUtil.logger(chainId).error("Local is not Moon net，can not create CrossGroup");
            return;
        }
        NodeGroupManager nodeGroupManager = NodeGroupManager.getInstance();
        NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByMagic(magicNumber);
        if (null != nodeGroup) {
            LoggerUtil.logger(chainId).error("getNodeGroupByMagic: nodeGroup  exist");
            return;
        }
        nodeGroup = new NodeGroup(magicNumber, chainId, maxIn, maxOut, minAvailableCount);
        //存储nodegroup
        nodeGroupPos.add((GroupPo) nodeGroup.parseToPo());
        StorageManager.getInstance().getDbService().saveNodeGroups(nodeGroupPos);
        nodeGroupManager.addNodeGroup(nodeGroup.getChainId(), nodeGroup);
        // 发送地址请求列表
        if (networkConfig.isMoonNode()) {
            MessageManager.getInstance().sendGetCrossAddressMessage(nodeGroupManager.getMoonMainNet(), nodeGroup, false, true, true);
        } else {
            MessageManager.getInstance().sendGetCrossAddressMessage(nodeGroup, nodeGroup, false, true, true);
        }
    }

    /**
     * nw_activeCross
     * 友链激活跨链
     */
    public void activeCross(int chainId, int maxOut, int maxIn, String seedIps) {
        if (0 == maxOut) {
            maxOut = networkConfig.getMaxOutCount();
        }
        if (0 == maxIn) {
            maxIn = networkConfig.getMaxInCount();
        }
        NodeGroupManager nodeGroupManager = NodeGroupManager.getInstance();
        LoggerUtil.logger(chainId).info("chainId={},seedIps={}", chainId, seedIps);
        //友链的跨链协议调用
        NodeGroup nodeGroup = nodeGroupManager.getNodeGroupByChainId(chainId);
        if (null == nodeGroup) {
            LoggerUtil.logger(chainId).error("getNodeGroupByMagic is null");
            return;
        }
        if (chainId != nodeGroup.getChainId()) {
            LoggerUtil.logger(chainId).error("chainId != nodeGroup.getChainId()");
            return;
        }
        nodeGroup.setMaxCrossIn(maxIn);
        nodeGroup.setMaxCrossOut(maxOut);
        List<String> ipList = new ArrayList<>();
        if (StringUtils.isNotBlank(seedIps)) {
            String[] ips = seedIps.split(NetworkConstant.COMMA);
            Collections.addAll(ipList, ips);
        }
        for (String croosSeed : ipList) {
            String[] crossAddr = croosSeed.split(NetworkConstant.COLON);
            nodeGroup.addNeedCheckNode(crossAddr[0], Integer.parseInt(crossAddr[1]), Integer.parseInt(crossAddr[1]), true);
        }
        nodeGroup.setCrossActive(true);
    }

    /**
     * nw_getGroupByChainId
     * 查看指定网络组信息
     *
     * @return
     */
    public NodeGroupVo getGroupByChainId(int chainId) {
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        return buildNodeGroupVo(nodeGroup);
    }

    private NodeGroupVo buildNodeGroupVo(NodeGroup nodeGroup) {
        NodeGroupVo nodeGroupVo = new NodeGroupVo();
        nodeGroupVo.setChainId(nodeGroup.getChainId());
        nodeGroupVo.setMagicNumber(nodeGroup.getMagicNumber());
        nodeGroupVo.setConnectCount(nodeGroup.getLocalNetNodeContainer().getConnectedNodes().size());
        nodeGroupVo.setDisConnectCount(nodeGroup.getLocalNetNodeContainer().getCanConnectNodes().size()
                + nodeGroup.getLocalNetNodeContainer().getDisconnectNodes().size() +
                nodeGroup.getLocalNetNodeContainer().getUncheckNodes().size() +
                nodeGroup.getLocalNetNodeContainer().getFailNodes().size());
        nodeGroupVo.setConnectCrossCount(nodeGroup.getCrossNodeContainer().getConnectedNodes().size());
        nodeGroupVo.setDisConnectCrossCount(nodeGroup.getCrossNodeContainer().getCanConnectNodes().size()
                + nodeGroup.getCrossNodeContainer().getDisconnectNodes().size() +
                nodeGroup.getCrossNodeContainer().getUncheckNodes().size());
        nodeGroupVo.setInCount(nodeGroup.getLocalNetNodeContainer().getConnectedCount(Node.IN));
        nodeGroupVo.setOutCount(nodeGroup.getLocalNetNodeContainer().getConnectedCount(Node.OUT));
        nodeGroupVo.setInCrossCount(nodeGroup.getCrossNodeContainer().getConnectedCount(Node.IN));
        nodeGroupVo.setOutCrossCount(nodeGroup.getCrossNodeContainer().getConnectedCount(Node.OUT));
        //网络连接，并能正常使用
        if (nodeGroup.isMoonCrossGroup()) {
            nodeGroupVo.setIsActive(nodeGroup.isActive(true) ? 1 : 0);
        } else {
            nodeGroupVo.setIsActive(nodeGroup.isActive(false) ? 1 : 0);
        }
        //跨链模块是否可用
        nodeGroupVo.setIsCrossActive(nodeGroup.isCrossActive() ? 1 : 0);
        nodeGroupVo.setIsMoonNet(nodeGroup.isMoonGroup() ? 1 : 0);
        nodeGroupVo.setTotalCount(nodeGroupVo.getInCount() + nodeGroupVo.getOutCount() + nodeGroupVo.getInCrossCount() + nodeGroupVo.getOutCrossCount());
        return nodeGroupVo;
    }

    /**
     * nw_getChainConnectAmount
     * 查看指定网络组信息
     */
    public int getChainConnectAmount(int chainId, boolean isCross) {
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        if (null == nodeGroup) {
            return 0;
        } else {
            return nodeGroup.getAvailableNodes(isCross).size();
        }
    }


    /**
     * nw_delNodeGroup
     * 注销指定网络组信息
     */
    public void delGroupByChainId(int chainId) {
        StorageManager.getInstance().getDbService().deleteGroup(chainId);
        //删除网络连接
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        nodeGroup.destroy();
    }

    /**
     * nw_getSeeds
     * 查询跨链种子节点
     */
    public List<String> getCrossSeeds() {
        return networkConfig.getMoonSeedIpList();
    }

    /**
     * @return
     */

    public long getMainMagicNumber() {
        return networkConfig.getPacketMagic();
    }

    /**
     * nw_reconnect
     * 重连网络
     */
    public void reconnect(int chainId) {
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        //默认只对自有网络进行重连接
        nodeGroup.reconnect(false);
    }

    /**
     * nw_getGroups
     * 获取链组信息
     *
     * @return
     */
    public List<NodeGroupVo> getGroups(int startPage, int pageSize) {
        NodeGroupManager nodeGroupManager = NodeGroupManager.getInstance();
        List<NodeGroup> nodeGroups = nodeGroupManager.getNodeGroups();
        int total = nodeGroups.size();
        List<NodeGroupVo> pageList = new ArrayList<>();
        if (startPage == 0 && pageSize == 0) {
            for (NodeGroup nodeGroup : nodeGroups) {
                pageList.add(buildNodeGroupVo(nodeGroup));
            }
        } else {
            int currIdx = (startPage > 1 ? (startPage - 1) * pageSize : 0);
            for (int i = 0; i < pageSize && i < (total - currIdx); i++) {
                NodeGroup nodeGroup = nodeGroups.get(currIdx + i);
                NodeGroupVo nodeGroupVo = buildNodeGroupVo(nodeGroup);
                pageList.add(nodeGroupVo);
            }
        }
        return pageList;
    }


    public long currentTimeMillis() {
        return TimeManager.currentTimeMillis();
    }

    public Map<String, Object> getNetworkInfo(int chainId) {
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        Map<String, Object> res = new HashMap<>(5);
        List<Node> nodes = nodeGroup.getLocalNetNodeContainer().getAvailableNodes();
        long localBestHeight;
        long netBestHeight = 0;
        int inCount = 0;
        int outCount = 0;
        BlockRpcService blockRpcService = SpringLiteContext.getBean(BlockRpcServiceImpl.class);
        for (Node node : nodes) {
            if (node.getBlockHeight() > netBestHeight) {
                netBestHeight = node.getBlockHeight();
            }
            if (node.getType() == Node.IN) {
                inCount++;
            } else {
                outCount++;
            }
        }
        if (nodeGroup.isMoonCrossGroup()) {
            localBestHeight = 0;
        } else {
            localBestHeight = blockRpcService.getBestBlockHeader(chainId).getBlockHeight();
        }
        //本地最新高度
        res.put("localBestHeight", localBestHeight);
        //网络最新高度
        if (localBestHeight > netBestHeight) {
            netBestHeight = localBestHeight;
        }
        res.put("netBestHeight", netBestHeight);
        //网络时间偏移
        res.put("timeOffset", TimeManager.netTimeOffset);
        //被动连接节点数量
        res.put("inCount", inCount);
        //主动连接节点数量
        res.put("outCount", outCount);
        return res;
    }

    public List<Map<String, Object>> getNetworkNodeList(int chainId) {
        List<Map<String, Object>> res = new ArrayList<>();
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByChainId(chainId);
        List<Node> nodes = new ArrayList<>();
        nodes.addAll(nodeGroup.getLocalNetNodeContainer().getAvailableNodes());
        nodes.addAll(nodeGroup.getCrossNodeContainer().getAvailableNodes());
        for (Node node : nodes) {
            Map<String, Object> data = new HashMap<>();
            //ip:port
            data.put("peer", node.getId());
            data.put("blockHeight", node.getBlockHeight());
            data.put("blockHash", node.getBlockHash());
            res.add(data);
        }
        return res;
    }
}

