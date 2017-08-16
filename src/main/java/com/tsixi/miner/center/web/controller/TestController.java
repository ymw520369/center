/*
 * Copyright (c) 2017. Chengdu Qianxing Technology Co.,LTD.
 * All Rights Reserved.
 */

package com.tsixi.miner.center.web.controller;

import org.alan.mars.MarsContext;
import com.tsixi.miner.center.dao.PlatformAccountDao;
import com.tsixi.miner.center.user.PlatformAccMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created on 2017/3/22.
 *
 * @author Alan
 * @since 1.0
 */
@RequestMapping("test")
@RestController
public class TestController {

    PlatformAccountDao platformAccountDao;

    @RequestMapping("test1")
    public String test1() {
        long beginTime = System.currentTimeMillis();
        int sum = 0;
        for (int i = 0; i < 100000000; i++) {
            sum++;
        }

        if (platformAccountDao == null) {
            platformAccountDao = MarsContext.getBean(PlatformAccountDao.class);
        }

        PlatformAccMapping pam = platformAccountDao.findByPfUserId("18786");
        long endTime = System.currentTimeMillis();
        return "test1 reloaded " + (endTime - beginTime) + ",sum=" + sum
                + ",pam" + pam.getPfUserName() + ",mydata=" + new MyData();
    }

    @RequestMapping("test2")
    public String test2() {
        return "test2 load...";
    }

    @RequestMapping("test3")
    public String test3() {
        return "test3 load...";
    }
}
