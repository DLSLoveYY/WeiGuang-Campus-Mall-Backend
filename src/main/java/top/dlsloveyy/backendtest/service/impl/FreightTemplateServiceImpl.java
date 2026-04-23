package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.FreightTemplate;
import top.dlsloveyy.backendtest.mapper.FreightTemplateMapper;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.FreightTemplateService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FreightTemplateServiceImpl implements FreightTemplateService {

    @Autowired
    private FreightTemplateMapper freightTemplateMapper;

    @Override
    public ResponseResult<?> listMyTemplates(Long sellerId) {
        List<FreightTemplate> templates = freightTemplateMapper.selectList(new LambdaQueryWrapper<FreightTemplate>()
                .eq(FreightTemplate::getSellerId, sellerId)
                .orderByDesc(FreightTemplate::getUpdateTime));
        return ResponseResult.success(templates);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> saveOrUpdate(Long sellerId, Map<String, Object> payload) {
        Long id = payload.get("id") == null ? null : Long.parseLong(payload.get("id").toString());
        FreightTemplate template = id == null ? new FreightTemplate() : freightTemplateMapper.selectById(id);
        if (template == null) {
            return ResponseResult.error(404, "模板不存在");
        }
        if (id != null && !sellerId.equals(template.getSellerId())) {
            return ResponseResult.error(403, "无权限修改");
        }

        template.setSellerId(sellerId);
        template.setTemplateName((String) payload.getOrDefault("templateName", "默认运费模板"));
        template.setBaseFee(payload.get("baseFee") == null ? BigDecimal.ZERO : new BigDecimal(payload.get("baseFee").toString()));
        template.setFreeShippingThreshold(payload.get("freeShippingThreshold") == null ? null : new BigDecimal(payload.get("freeShippingThreshold").toString()));
        template.setExtraFeePerItem(payload.get("extraFeePerItem") == null ? BigDecimal.ZERO : new BigDecimal(payload.get("extraFeePerItem").toString()));
        template.setEnabled(payload.get("enabled") == null ? 1 : Integer.parseInt(payload.get("enabled").toString()));
        template.setUpdateTime(LocalDateTime.now());

        if (id == null) {
            template.setCreateTime(LocalDateTime.now());
            freightTemplateMapper.insert(template);
            return ResponseResult.success("模板创建成功", template.getId());
        }

        freightTemplateMapper.updateById(template);
        return ResponseResult.success("模板更新成功", template.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> setEnabled(Long sellerId, Long templateId, Integer enabled) {
        FreightTemplate template = freightTemplateMapper.selectById(templateId);
        if (template == null || !sellerId.equals(template.getSellerId())) {
            return ResponseResult.error(404, "模板不存在");
        }
        template.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        template.setUpdateTime(LocalDateTime.now());
        freightTemplateMapper.updateById(template);
        return ResponseResult.success("状态更新成功", templateId);
    }
}
