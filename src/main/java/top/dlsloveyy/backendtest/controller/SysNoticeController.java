package top.dlsloveyy.backendtest.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.entity.SysNotice;
import top.dlsloveyy.backendtest.mapper.SysNoticeMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/sysNotice")
public class SysNoticeController {

    @Autowired
    private SysNoticeMapper sysNoticeMapper;

    /**
     * 获取系统公告列表（分页，按创建时间倒序）
     * 公开接口，无需认证
     */
    @GetMapping("/list")
    public Map<String, Object> listNotices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<SysNotice> noticePage = new Page<>(page, size);
        LambdaQueryWrapper<SysNotice> query = new LambdaQueryWrapper<>();
        query.orderByDesc(SysNotice::getCreateTime);

        sysNoticeMapper.selectPage(noticePage, query);

        return Map.of(
                "code", 200,
                "data", noticePage.getRecords(),
                "total", noticePage.getTotal()
        );
    }
}
