package io.nuls.account.rpc.cmd;

import io.nuls.account.util.LoggerUtil;
import io.nuls.core.basic.AddressTool;
import io.nuls.core.core.annotation.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: qinyifeng
 * @description:
 * @date: 2018/11/5
 */
@Component
public class AddressPrefixCmd {

    public List<Map<String, Object>> getAllAddressPrefix(Map params) {
        List<Map<String, Object>> rtList = new ArrayList<>();
        Map<Integer, String> addressPreFixMap = AddressTool.getAddressPreFixMap();
        for (Map.Entry<Integer, String> entry : addressPreFixMap.entrySet()) {
            Map<String, Object> rtValue = new HashMap<>();
            rtValue.put("chainId", entry.getKey());
            rtValue.put("addressPrefix", entry.getValue());
            rtList.add(rtValue);
        }
        return rtList;
    }

    public Map<String, Object> getAddressPrefixByChainId(int chainId) {
        Map<String, Object> rtValue = new HashMap<>();
        Map<Integer, String> addressPreFixMap = new HashMap<>();
        rtValue.put("chainId", addressPreFixMap.get(chainId));
        rtValue.put("addressPrefix", chainId);
        return rtValue;
    }

    public void addAddressPrefix(List<Map<String, Object>> prefixList) {
        for (Map<String, Object> prefixMap : prefixList) {
            AddressTool.addPrefix(Integer.parseInt(prefixMap.get("chainId").toString()), String.valueOf(prefixMap.get("addressPrefix")));
            LoggerUtil.LOG.debug("chainId={},prefix={}", prefixMap.get("chainId"), prefixMap.get("addressPrefix"));
        }
    }
}
