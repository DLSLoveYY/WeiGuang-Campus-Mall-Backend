package top.dlsloveyy.backendtest.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nadmin")
@RequiredArgsConstructor
public class NormalAdminController {

    @Autowired
    private AdminController adminController;

    @GetMapping("/goods/adminPage")
    public ResponseEntity<?> getGoodsPage(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestHeader("Authorization") String authHeader) {
        return adminController.getGoodsPage(page, size, null, authHeader);
    }

    @GetMapping("/goods/search")
    public ResponseEntity<?> searchGoods(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestHeader("Authorization") String authHeader) {
        return adminController.getGoodsPage(page, size, keyword, authHeader);
    }

    @PostMapping("/goods/delete")
    public ResponseEntity<?> deleteGoodsByNadmin(@RequestBody Map<String, Long> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        return adminController.takedownGoods(payload, authHeader);
    }

    @PostMapping("/goods/toggleFeatured")
    public ResponseEntity<?> toggleFeatured(@RequestBody Map<String, Long> payload,
                                            @RequestHeader("Authorization") String authHeader) {
        return adminController.toggleGoodsFeatured(payload, authHeader);
    }

    @GetMapping("/checkGoods/page")
    public ResponseEntity<?> getCheckGoodsPage(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestHeader("Authorization") String authHeader) {
        return adminController.getCheckGoodsPage(page, size, authHeader);
    }

    @GetMapping("/checkGoods/search")
    public ResponseEntity<?> searchCheckGoods(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestHeader("Authorization") String authHeader) {
        return adminController.searchCheckGoods(keyword, page, size, authHeader);
    }

    @PostMapping("/checkGoods/approve")
    public ResponseEntity<?> approveGoods(@RequestBody Map<String, Long> payload,
                                          @RequestHeader("Authorization") String authHeader) {
        return adminController.approveCheckGoods(payload, authHeader);
    }

    @PostMapping("/checkGoods/reject")
    public ResponseEntity<?> rejectCheckGoods(@RequestBody Map<String, Long> payload,
                                              @RequestHeader("Authorization") String authHeader) {
        return adminController.rejectCheckGoods(payload, authHeader);
    }

    @PostMapping("/announce")
    public ResponseEntity<?> publishAnnouncement(@RequestBody Map<String, String> payload,
                                                 @RequestHeader("Authorization") String authHeader) {
        return adminController.publishAnnouncement(payload, authHeader);
    }
}
