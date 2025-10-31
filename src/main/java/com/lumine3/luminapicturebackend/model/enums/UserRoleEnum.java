package com.lumine3.luminapicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 用户角色的枚举类
 * 用户 和 管理员
 */
@Getter
public enum UserRoleEnum {
    USER("用户", "user"),
    ADMIN("管理员", "admin");


    private final String text;
    private final String value;

    private UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 写几个方法用于快速获取枚举类
     */

    /**
     * 根据value获取枚举类
     *
     * @param value
     * @return
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum e : UserRoleEnum.values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
