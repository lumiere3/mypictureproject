package com.lumine3.luminapicturebackend.model.dto.user;


import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册的请求体类
 */
@Data
public class UserRegisterRequest implements Serializable {
    private static final long serialVersionUID = 2906486587297516309L;
    /**
     * 用户名
     */
    private String userAccount;
    /**
     * 用户密码
     */
    private String userPassword;
    /**
     * 确认密码
     */
    private String confirmPassword;

}
