package top.dlsloveyy.backendtest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.dlsloveyy.backendtest.entity.UserAddress;
import top.dlsloveyy.backendtest.mapper.UserAddressMapper;
import top.dlsloveyy.backendtest.model.dto.ResponseResult;
import top.dlsloveyy.backendtest.service.UserAddressService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserAddressServiceImpl implements UserAddressService {

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Override
    public ResponseResult<?> list(Long userId) {
        List<UserAddress> list = userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdateTime));
        return ResponseResult.success(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> add(Long userId, Map<String, Object> payload) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(readString(payload, "receiverName"));
        address.setReceiverPhone(readString(payload, "receiverPhone", "contactPhone"));
        address.setProvince(readString(payload, "province"));
        address.setCity(readString(payload, "city"));
        address.setDistrict(readString(payload, "district"));
        address.setDetail(readString(payload, "detail", "detailAddress"));

        ResponseResult<?> validation = validateAddressPayload(address);
        if (validation != null) {
            return validation;
        }

        Integer isDefault = parseIsDefault(payload);
        address.setIsDefault(isDefault);
        address.setCreateTime(LocalDateTime.now());
        address.setUpdateTime(LocalDateTime.now());

        if (isDefault == 1) {
            clearDefault(userId);
        }

        userAddressMapper.insert(address);
        return ResponseResult.success("地址新增成功", address.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> update(Long userId, Long addressId, Map<String, Object> payload) {
        UserAddress existed = userAddressMapper.selectById(addressId);
        if (existed == null || !userId.equals(existed.getUserId())) {
            return ResponseResult.error(404, "地址不存在");
        }

        String receiverName = readString(payload, "receiverName");
        String receiverPhone = readString(payload, "receiverPhone", "contactPhone");
        String province = readString(payload, "province");
        String city = readString(payload, "city");
        String district = readString(payload, "district");
        String detail = readString(payload, "detail", "detailAddress");

        if (receiverName != null) existed.setReceiverName(receiverName);
        if (receiverPhone != null) existed.setReceiverPhone(receiverPhone);
        if (province != null) existed.setProvince(province);
        if (city != null) existed.setCity(city);
        if (district != null) existed.setDistrict(district);
        if (detail != null) existed.setDetail(detail);

        ResponseResult<?> validation = validateAddressPayload(existed);
        if (validation != null) {
            return validation;
        }

        Integer isDefault = payload.get("isDefault") == null ? existed.getIsDefault() : parseIsDefault(payload);
        existed.setIsDefault(isDefault);
        existed.setUpdateTime(LocalDateTime.now());

        if (isDefault == 1) {
            clearDefault(userId);
        }

        userAddressMapper.updateById(existed);
        return ResponseResult.success("地址更新成功", existed.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> remove(Long userId, Long addressId) {
        UserAddress existed = userAddressMapper.selectById(addressId);
        if (existed == null || !userId.equals(existed.getUserId())) {
            return ResponseResult.error(404, "地址不存在");
        }
        
        // 如果删除的是默认地址，需要重新分配默认地址
        boolean isDefault = Integer.valueOf(1).equals(existed.getIsDefault());
        userAddressMapper.deleteById(addressId);
        
        if (isDefault) {
            // 删除默认地址后，查询该用户剩余的其他地址
            List<UserAddress> remainingAddresses = userAddressMapper.selectList(
                    new LambdaQueryWrapper<UserAddress>()
                            .eq(UserAddress::getUserId, userId)
                            .orderByDesc(UserAddress::getUpdateTime)
                            .last("limit 1"));
            
            // 如果还有其他地址，将最后更新的地址设为默认
            if (!remainingAddresses.isEmpty()) {
                UserAddress newDefault = remainingAddresses.get(0);
                newDefault.setIsDefault(1);
                newDefault.setUpdateTime(LocalDateTime.now());
                userAddressMapper.updateById(newDefault);
            }
        }
        
        return ResponseResult.success("地址删除成功", addressId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> setDefault(Long userId, Long addressId) {
        UserAddress existed = userAddressMapper.selectById(addressId);
        if (existed == null || !userId.equals(existed.getUserId())) {
            return ResponseResult.error(404, "地址不存在");
        }
        clearDefault(userId);
        existed.setIsDefault(1);
        existed.setUpdateTime(LocalDateTime.now());
        userAddressMapper.updateById(existed);
        return ResponseResult.success("默认地址设置成功", addressId);
    }

    private void clearDefault(Long userId) {
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0)
                .set(UserAddress::getUpdateTime, LocalDateTime.now()));
    }

    private String readString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                return text.isEmpty() ? null : text;
            }
        }
        return null;
    }

    /**
     * 安全地解析 isDefault 参数，支持多种类型
     * 支持：布尔值(true=1, false=0)、整数(1/0)、字符串("1"/"0")
     */
    private Integer parseIsDefault(Map<String, Object> payload) {
        Object value = payload.get("isDefault");
        if (value == null) {
            return 0;
        }
        
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? 1 : 0;
        }
        
        if (value instanceof Integer) {
            Integer intValue = (Integer) value;
            return (intValue == 1) ? 1 : 0;
        }
        
        if (value instanceof Number) {
            return (((Number) value).intValue() == 1) ? 1 : 0;
        }
        
        try {
            return (Integer.parseInt(value.toString()) == 1) ? 1 : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private ResponseResult<?> validateAddressPayload(UserAddress address) {
        if (address.getReceiverName() == null || address.getReceiverName().isBlank()) {
            return ResponseResult.error(400, "收件人不能为空");
        }
        if (address.getReceiverPhone() == null || address.getReceiverPhone().isBlank()) {
            return ResponseResult.error(400, "联系电话不能为空");
        }
        // 验证电话号码格式（11位手机号或其他格式）
        String phone = address.getReceiverPhone().trim();
        if (!phone.matches("^1\\d{10}$") && !phone.matches("^\\d{7,11}$")) {
            return ResponseResult.error(400, "联系电话格式不正确");
        }
        if (address.getProvince() == null || address.getCity() == null || address.getDistrict() == null) {
            return ResponseResult.error(400, "请选择完整的省市区");
        }
        if (address.getDetail() == null || address.getDetail().isBlank()) {
            return ResponseResult.error(400, "详细地址不能为空");
        }
        return null;
    }
}
