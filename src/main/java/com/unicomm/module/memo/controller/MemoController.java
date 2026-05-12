package com.unicomm.module.memo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 备忘录控制器 (骨架).
 *
 * <p>Phase 1: 暂不实现业务逻辑，仅保留骨架代码.</p>
 *
 * @author UniComm Team
 * @version 0.1.0
 */
@RestController
@RequestMapping("/api/v1/memo")
@Tag(name = "备忘录模块", description = "备忘录管理接口 (Phase 1 骨架)")
public class MemoController {

    /**
     * 备忘录列表 (待实现).
     */
    @RequestMapping("/list")
    @Operation(summary = "备忘录列表", description = "获取备忘录列表 (待实现)")
    public String list() {
        return "Phase 1: Memo module not implemented yet";
    }
}
