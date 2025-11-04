package com.lumine3.luminapicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumine3.luminapicturebackend.model.dto.user.UserAddRequest;
import com.lumine3.luminapicturebackend.model.dto.user.UserQueryRequest;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumine3.luminapicturebackend.model.vo.LoginUserVO;
import com.lumine3.luminapicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Asus
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-10-31 16:27:14
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param confirmPassword
     * @param userAccount
     * @param userPassword
     * @return
     */
    long userRegister(String userAccount, String userPassword, String confirmPassword);


    /**
     * 用户登录
     *
     * @param userAccount
     * @param userPassword
     * @param request
     * @return
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取当前用户, 用于系统内部校验和其他流程, 不会返回给前端
     * @param request 请求
     * @return USer -> 系统内部使用, 直接返回User
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取加密后的密码
     *
     * @param password
     * @return
     */
    String doPasswordEncryption(String password);

    /**
     * 获取登录后的用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户注销
     * @param request 注销 -> 退出登录
     * @return 布尔
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 把User转成UserVO 一条数据
     *
     * @param user 原信息
     * @return UserVO 进行脱敏
     */
    UserVO getUserVO(User user);

    /**
     * 转换 多条数据
     *
     * @param users 原信息
     * @return UserVO 进行脱敏
     */
    List<UserVO> getUserVOList(List<User> users);

    /**
     * *通过请求,获取查询条件 Wrapper
     * @param userQueryRequest 查询请求体
     * @return QueryWrapper
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 管理员创建一个新用户
     * @param userAddRequest 管理员添加用户的请求类
     * @return 布尔
     */
    long addUserByAdmin(UserAddRequest userAddRequest);
}
