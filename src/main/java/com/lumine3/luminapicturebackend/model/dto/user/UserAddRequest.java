package com.lumine3.luminapicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求, 对于管理员使用
 */
@Data
public class UserAddRequest implements Serializable {

    private static final long serialVersionUID = -6858394774480057846L;
    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色: user, admin
     */
    private String userRole;


}
