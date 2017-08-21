/**
 * Copyright Chengdu Qianxing Technology Co.,LTD.
 * All Rights Reserved.
 * <p>
 * 2017年2月18日
 */
package com.tsixi.miner.center.web.controller;

import com.alibaba.fastjson.JSON;
import com.google.gson.JsonObject;
import org.alan.mars.config.NodeConfig;
import org.alan.mars.curator.MarsNode;
import org.alan.mars.curator.NodeManager;
import org.alan.mars.curator.NodeType;
import org.alan.mars.data.UserInfo;
import org.alan.mars.uid.UidDao;
import org.alan.mars.uid.UidTypeEnum;
import com.tsixi.miner.center.config.TsdkConfig;
import com.tsixi.miner.center.dao.PlatformAccountDao;
import com.tsixi.miner.center.user.PlatformAccMapping;
import com.tsixi.miner.center.web.been.CertifyInfo;
import com.tsixi.miner.center.web.result.GeneralResult;
import org.alan.utils.HttpUtils;
import org.alan.utils.MD5Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.alan.mars.curator.NodeType.DATA;
import static org.alan.mars.curator.NodeType.GATE;

/**
 * @author Alan
 * @scene 1.0
 */
@RequestMapping("user")
@RestController
public class CertifyAccountController {

    Logger log = LoggerFactory.getLogger(getClass());

    private PlatformAccountDao platformAccountDao;

    private UidDao uidDao;

    private TsdkConfig tsdkConfig;

    private NodeConfig nodeConfig;

    private RedisTemplate<String, Object> redisTemplate;

    private NodeManager nodeManager;

    @Autowired
    public CertifyAccountController(PlatformAccountDao platformAccountDao,
                                    UidDao uidDao, TsdkConfig tsdkConfig,
                                    NodeConfig nodeConfig, RedisTemplate<String, Object> redisTemplate,
                                    NodeManager nodeManager) {
        this.platformAccountDao = platformAccountDao;
        this.uidDao = uidDao;
        this.tsdkConfig = tsdkConfig;
        this.nodeConfig = nodeConfig;
        this.redisTemplate = redisTemplate;
        this.nodeManager = nodeManager;
    }

    @RequestMapping("certify")
    public Object certify(CertifyInfo certifyInfo, HttpServletRequest request)
            throws Exception {
        if (log.isInfoEnabled()) {
            log.info("用户登录验证信息{}", certifyInfo);
        }
        if (certifyInfo == null) {
            return GeneralResult.FAIL.setDec("验证信息为空");
        }
        // 向TSDK服务器验证token
        String token = certifyInfo.getToken();
        String userId = certifyInfo.getUserId();
        String signSource = new StringBuilder("userID=").append(userId)
                .append("token=").append(token).append(tsdkConfig.appKey)
                .toString();
        String sign = MD5Utils.md5Digest(signSource);
        log.info("md5 sign source is {},sign is {}", signSource, sign);
        Map<String, String> param = new HashMap<>();
        param.put("token", token);
        param.put("userID", userId);
        param.put("sign", sign);
        String url = tsdkConfig.url + tsdkConfig.certifyPath;
        HttpUtils.HttpResponse httpResponse = HttpUtils.doPost(url, param);
        if (!httpResponse.isOk()) {
            log.warn("TSDK消息验证失败,http响应码为 {}", httpResponse.getStatusCode());
            return GeneralResult.FAIL.setDec("TSDK消息验证失败");
        }
        Integer state = httpResponse.getIntValue("state");
        if (state == null || state != 1) {
            log.warn("TSDK消息验证失败,返回状态码 {}", state);
            return GeneralResult.FAIL.setDec("TSDK消息验证失败");
        }
        JsonObject jsonObject = httpResponse.getJsonObject("data");
        userId = jsonObject.get("userID").getAsString();
        String userName = jsonObject.get("username").getAsString();
        // 账号验证成功，返回用户ID、登录凭证、逻辑服务器地址
        PlatformAccMapping pam = platformAccountDao.findByPfUserId(userId);
        long accountId;
        if (pam == null) {
            accountId = uidDao.getAndUpdateUid(UidTypeEnum.USER_ID, 1);
            pam = new PlatformAccMapping(userId, userName, accountId);
            platformAccountDao.save(pam);
        }
        accountId = pam.getAccountId();

        NodeType nodeType = nodeConfig.isUseGate() ? GATE : DATA;
        NodeConfig config = getDataNodeConfig(nodeType);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("accountId", accountId);
        data.put("logicServer", config.getTcpAddress());

        HashOperations<String, String, UserInfo> hashOperations = redisTemplate.opsForHash();
        UserInfo userData = hashOperations.get(UserInfo.USER_INFO, token);
        if (userData == null) {
            userData = new UserInfo();
            userData.token = token;
        }
        userData.userId = accountId;
        userData.userName = userName;
        userData.logicAddress = config.getTcpAddress();
        userData.online = true;
        hashOperations.put(UserInfo.USER_INFO, token, userData);

        log.info("账号验证成功,返回状态码 {}", state);
        // 将玩家的登陆数据缓存到redis中，方便其他服务器进行验证
        redisTemplate.opsForValue().set(token, data, 60, TimeUnit.MINUTES);
        return GeneralResult.SUCCESS.setData(data).setDec("验证成功");
    }

    public NodeConfig getDataNodeConfig(NodeType nodeType) {
        // 获得数据服务器节点
        MarsNode marNode = nodeManager.getMarNode(nodeType);
        if (marNode == null || !marNode.hasChildren()) {
            log.warn("data node is null or empty.");
            return null;
        }
        // 随机一个子节点
        marNode = marNode.randomOneMarsNode();
        String data = marNode.getNodeData();
        NodeConfig nodeConfig = JSON.parseObject(data, NodeConfig.class);
        return nodeConfig;
    }
}
