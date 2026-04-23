package top.dlsloveyy.backendtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.AccountService;
import top.dlsloveyy.backendtest.util.JwtUtil;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(token.substring(7));
        }
        return null;
    }

    @GetMapping("/ledger")
    public ResponseResult<?> ledger(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return accountService.listLedger(userId);
    }

    @GetMapping("/balance")
    public ResponseResult<?> balance(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return ResponseResult.error(401, "请先登录");
        }
        return ResponseResult.success(accountService.getBalanceSnapshot(userId));
    }
}
