package top.dlsloveyy.backendtest.service;

import top.dlsloveyy.backendtest.model.dto.ResponseResult;

import java.util.Map;

public interface UserAddressService {

    ResponseResult<?> list(Long userId);

    ResponseResult<?> add(Long userId, Map<String, Object> payload);

    ResponseResult<?> update(Long userId, Long addressId, Map<String, Object> payload);

    ResponseResult<?> remove(Long userId, Long addressId);

    ResponseResult<?> setDefault(Long userId, Long addressId);
}
