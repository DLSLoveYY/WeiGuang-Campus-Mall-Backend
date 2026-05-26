package top.dlsloveyy.backendtest.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.UserAddressService;
import top.dlsloveyy.backendtest.util.JwtUtil;

import java.util.Map;

@RestController
@RequestMapping("/api/address")
public class UserAddressController {

    @Autowired
    private UserAddressService userAddressService;

    @Autowired
    private JwtUtil jwtUtil;

    private Long getUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(token.substring(7));
        }
        return null;
    }

    @GetMapping("/list")
    public ResponseResult<?> list(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return userAddressService.list(userId);
    }

    @PostMapping
    public ResponseResult<?> add(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return userAddressService.add(userId, payload);
    }

    @PutMapping("/{id}")
    public ResponseResult<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> payload,
                                    HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return userAddressService.update(userId, id, payload);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> remove(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return userAddressService.remove(userId, id);
    }

    @PutMapping("/{id}/default")
    public ResponseResult<?> setDefault(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return userAddressService.setDefault(userId, id);
    }

    @GetMapping("/geocode")
    public ResponseResult<?> geocode(@RequestParam("address") String address,
                                     HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        if (address == null || address.isBlank()) return ResponseResult.error(400, "地址不能为空");
        Map<String, Object> data = userAddressService.geocodeTextAddress(address);
        Object longitude = data.get("longitude");
        Object latitude = data.get("latitude");
        if (longitude == null || latitude == null) {
            return ResponseResult.error(404, "未找到匹配地址，请补充更精确的省市区和详细地址");
        }
        return ResponseResult.success("地址解析成功", data);
    }

    @GetMapping("/reverse-geocode")
    public ResponseResult<?> reverseGeocode(@RequestParam("lng") Double longitude,
                                            @RequestParam("lat") Double latitude,
                                            HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return ResponseResult.error(401, "请先登录");
        return userAddressService.reverseGeocode(userId, longitude, latitude);
    }
}
