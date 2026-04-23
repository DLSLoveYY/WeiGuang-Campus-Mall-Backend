package top.dlsloveyy.backendtest.service;

import top.dlsloveyy.backendtest.model.dto.ResponseResult;

import java.util.Map;

public interface FreightTemplateService {

    ResponseResult<?> listMyTemplates(Long sellerId);

    ResponseResult<?> saveOrUpdate(Long sellerId, Map<String, Object> payload);

    ResponseResult<?> setEnabled(Long sellerId, Long templateId, Integer enabled);
}
