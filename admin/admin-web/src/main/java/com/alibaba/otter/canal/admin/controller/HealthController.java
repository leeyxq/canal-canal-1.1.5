package com.alibaba.otter.canal.admin.controller;

import com.google.common.collect.ImmutableMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器
 *
 * @author lixiangqian
 * @since 2022/2/25 12:22
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Object health() {
        return ImmutableMap.of("status", 200, "message", "success");
    }
}