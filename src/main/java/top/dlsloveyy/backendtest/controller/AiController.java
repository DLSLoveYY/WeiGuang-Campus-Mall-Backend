package top.dlsloveyy.backendtest.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.dlsloveyy.backendtest.config.JwtFilter;
import top.dlsloveyy.backendtest.entity.User;
import top.dlsloveyy.backendtest.service.AiService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 大模型接入（豆包 / 火山方舟）。
 * - /api/ai/support/chat   智能客服
 * - /api/ai/goods/describe 一键生成商品描述
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private AiService aiService;

    // ---------- 功能 1：AI 客服回复 ----------
    private static final String SUPPORT_SYSTEM_PROMPT =
            "你是“微光校园闲置商城”的智能客服助手。平台是一个校园二手闲置交易平台，主要用户是学生群体，交易方式包含“校园面交”和“邮寄”。\n" +
            "请遵循以下规则：\n" +
            "1. 回答要简洁、条理清晰，优先使用带编号的分步指引（最多 6 步）。\n" +
            "2. 只回答与商城业务相关的问题：商品发布、下单、支付与余额、退款与争议、物流与面交、账号安全、举报与信用、平台规则。\n" +
            "3. 遇到用户描述模糊时，先给出通用处理指引，并提示用户补充订单号、时间、截图等关键信息。\n" +
            "4. 涉及金额赔付、封号解封、隐私纠纷等敏感问题，必须引导用户“提交客服工单由人工处理”，不擅自承诺结果。\n" +
            "5. 不要编造平台没有的功能，也不要给出与平台业务无关的法律/医疗/投资建议。\n" +
            "6. 保持礼貌、克制的语气，避免使用感叹号堆砌。\n" +
            "7. 如果用户问的是明显的常识问题或闲聊，可以简短回答一句后引导回到商城相关问题。\n" +
            "\n" +
            "平台常见问题参考（供你总结回答时使用，不要原样复述）：\n" +
            "- 订单显示“交易中”：确认卖家是否发货/移交，买家是否确认收货；状态异常请提交订单号走工单。\n" +
            "- 取消订单：未发货/未面交前可直接取消；已发货需走退款流程。\n" +
            "- 发起退款需要：订单号、问题照片或视频、聊天记录、快递单号（如有）。\n" +
            "- 争议处理时效：通常 24-48 小时，复杂情况会通过通知中心提醒。\n" +
            "- 余额充值未到账：刷新余额或等 3-5 分钟，仍异常提供充值时间和金额。\n" +
            "- 退款到账：原路退回，1-3 个工作日。\n" +
            "- 面交地点修改：双方私信确认，并在订单详情更新地址。\n" +
            "- 邮寄填写物流：订单详情 -> 填写物流 -> 选择快递公司 + 输入单号。\n" +
            "- 账号被盗：立即改密码，提交工单申诉。\n" +
            "- 举报：商品详情或用户主页点击“举报”，上传证据，平台 24 小时内处理。";

    @PostMapping("/support/chat")
    public ResponseEntity<?> supportChat(@RequestBody Map<String, Object> body) {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }

        String message = body.get("message") == null ? "" : body.get("message").toString().trim();
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "消息不能为空"));
        }
        if (message.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "消息过长，请精简后再试"));
        }

        // 取最近几轮历史对话（前端传入 history: [{role, content}, ...]）
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SUPPORT_SYSTEM_PROMPT));

        Object historyObj = body.get("history");
        if (historyObj instanceof List<?> historyList) {
            int start = Math.max(0, historyList.size() - 8); // 仅保留最近 8 条，防止 prompt 过长
            for (int i = start; i < historyList.size(); i++) {
                Object item = historyList.get(i);
                if (item instanceof Map<?, ?> m) {
                    Object roleObj = m.get("role");
                    Object contentObj = m.get("content");
                    String role = roleObj == null ? "" : String.valueOf(roleObj);
                    String content = contentObj == null ? "" : String.valueOf(contentObj).trim();
                    if (content.isEmpty()) continue;
                    // 前端的 bot 对应 OpenAI 规范里的 assistant
                    if ("bot".equals(role) || "assistant".equals(role)) {
                        messages.add(Map.of("role", "assistant", "content", content));
                    } else if ("user".equals(role)) {
                        messages.add(Map.of("role", "user", "content", content));
                    }
                }
            }
        }
        messages.add(Map.of("role", "user", "content", message));

        try {
            String reply = aiService.chat(messages);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reply", reply);
            return ResponseEntity.ok(Map.of("code", 200, "data", data));
        } catch (Exception e) {
            log.warn("AI 客服调用失败: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "AI 客服暂时不可用，请稍后再试或提交客服工单"
            ));
        }
    }

    // ---------- 功能 2：一键生成商品描述 ----------
    private static final String GOODS_SYSTEM_PROMPT =
            "你是“微光校园闲置商城”的商品描述撰写助手，目标是根据卖家填写的商品结构化信息，生成适合在校园闲置交易场景展示的商品详情文案。\n" +
            "要求：\n" +
            "1. 输出为中文纯文本，不要使用 Markdown 的 # 号标题，不要使用代码块。\n" +
            "2. 语气自然、真诚、贴近学生卖家，避免浮夸营销词（如“震撼”“全网最低”）。\n" +
            "3. 结构建议：一段概述（1-2 句）+ 小节分点，小节使用“商品亮点：”“成色说明：”“适合人群：”“交易说明：”这类中文冒号标题，每节 2-4 条要点，每条前用「· 」。\n" +
            "4. 不要编造参数（如品牌、型号、配置），只能基于用户给的信息合理发挥；缺失的信息可用模糊表达（例如“日常使用无明显磕碰”）。\n" +
            "5. 结合商品新旧度和原价/二手价的差距自然写明性价比，但不要写具体折扣百分比（避免算错）。\n" +
            "6. 结尾可以附一句温和的邀约，例如欢迎到店详谈 / 欢迎咨询细节。\n" +
            "7. 总字数控制在 180-320 字。\n" +
            "8. 只输出最终的商品描述本身，不要输出任何解释、前言或“以下是描述”之类的话。";

    @PostMapping("/goods/describe")
    public ResponseEntity<?> generateGoodsDescription(@RequestBody Map<String, Object> body) {
        User currentUser = JwtFilter.currentUser.get();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "请先登录"));
        }

        String title = trimToEmpty(body.get("title"));
        String category = trimToEmpty(body.get("category"));
        String conditionLevel = trimToEmpty(body.get("conditionLevel"));
        String price = trimToEmpty(body.get("price"));
        String originalPrice = trimToEmpty(body.get("originalPrice"));
        String deliveryMethods = trimToEmpty(body.get("deliveryMethods"));
        String extra = trimToEmpty(body.get("extra"));

        if (title.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "请先填写商品标题"));
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请根据下列商品信息生成一段商品详情描述：\n");
        userPrompt.append("- 商品标题：").append(title).append("\n");
        if (!category.isEmpty())        userPrompt.append("- 分类：").append(category).append("\n");
        if (!conditionLevel.isEmpty())  userPrompt.append("- 新旧度：").append(conditionLevel).append("\n");
        if (!price.isEmpty())           userPrompt.append("- 二手价：").append(price).append(" 元\n");
        if (!originalPrice.isEmpty())   userPrompt.append("- 原价：").append(originalPrice).append(" 元\n");
        if (!deliveryMethods.isEmpty()) userPrompt.append("- 交易方式：").append(deliveryMethods).append("\n");
        if (!extra.isEmpty())           userPrompt.append("- 补充说明：").append(extra).append("\n");
        userPrompt.append("\n请直接输出描述文本。");

        try {
            String description = aiService.chat(GOODS_SYSTEM_PROMPT, userPrompt.toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("description", description);
            return ResponseEntity.ok(Map.of("code", 200, "data", data));
        } catch (Exception e) {
            log.warn("AI 商品描述生成失败: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500,
                    "message", "AI 描述生成失败，请稍后再试"
            ));
        }
    }

    private String trimToEmpty(Object obj) {
        return obj == null ? "" : String.valueOf(obj).trim();
    }
}
