package top.dlsloveyy.backendtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.FreightTemplateService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.util.Map;

@RestController
@RequestMapping("/api/freight-template")
public class FreightTemplateController {

    @Autowired
    private FreightTemplateService freightTemplateService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(token.substring(7));
        }
        return null;
    }

    @GetMapping("/my")
    public ResponseResult<?> myTemplates(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return freightTemplateService.listMyTemplates(userId);
    }

    @PostMapping("/save")
    public ResponseResult<?> save(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return freightTemplateService.saveOrUpdate(userId, payload);
    }

    @PutMapping("/{id}/enabled")
    public ResponseResult<?> setEnabled(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, Object> payload,
                                        HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        Integer enabled = payload == null || payload.get("enabled") == null ? 1 : Integer.parseInt(payload.get("enabled").toString());
        return freightTemplateService.setEnabled(userId, id, enabled);
    }
}
