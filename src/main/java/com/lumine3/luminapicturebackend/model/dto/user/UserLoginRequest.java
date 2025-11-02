package com.lumine3.luminapicturebackend.model.dto.user;


import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录的请求体类
 */
@Data
public class UserLoginRequest implements Serializable {
    private static final long serialVersionUID = -4944205145274250294L;
    /**
     * 用户名
     */
    private String userAccount;
    /**
     * 用户密码
     */
    private String userPassword;

}
