package com.lumine3.luminapicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumine3.luminapicturebackend.constant.UserConstant;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.model.dto.user.UserQueryRequest;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.enums.UserRoleEnum;
import com.lumine3.luminapicturebackend.model.vo.LoginUserVO;
import com.lumine3.luminapicturebackend.model.vo.UserVO;
import com.lumine3.luminapicturebackend.service.UserService;
import com.lumine3.luminapicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Asus
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-10-31 16:27:14
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 用户注册
     *
     * @param userPassword    用户账号
     * @param userAccount     用户密码
     * @param confirmPassword 确认密码
     * @return
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String confirmPassword) {
        // 参数校验
        // 1. 账号 , 密码 , 确认密码不能为空
        // 1.1 账号
        ThrowUtils.throwIf(StrUtil.isBlank(userAccount), ErrorCode.PARAMS_ERROR, "账号为空!");
        //1.2 密码
        ThrowUtils.throwIf(StrUtil.isBlank(userPassword), ErrorCode.PARAMS_ERROR, "密码为空!");
        //1.3确认密码
        ThrowUtils.throwIf(StrUtil.isBlank(confirmPassword), ErrorCode.PARAMS_ERROR, "确认密码为空!");
        //2.1 用户名和密码长度必须满足条件 用户名大于4 , 密码大于6
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "非法的账号长度!");
        ThrowUtils.throwIf(userPassword.length() < 6, ErrorCode.PARAMS_ERROR, "非法的密码长度!");
        //2.2 两次密码必须相同
        boolean equals = userPassword.equals(confirmPassword);
        ThrowUtils.throwIf(!equals, ErrorCode.PARAMS_ERROR, "两次密码不一致");
        //3. 用户账号不能重复
        /**
         * 用一下传统方法
         * mapper的方法
         */
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        //不能重复
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "用户账号已存在!");
        // 加密密码
        String encrypt = doPasswordEncryption(userPassword);
        //构造新用户
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encrypt);
        user.setUserName("新用户");
        user.setUserRole(UserRoleEnum.USER.getValue());
        //将新用户的信息插入数据库
        boolean saved = this.save(user);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "数据库错误, 插入新用户失败!");
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @param request      请求 用于设置登录态
     * @return VO
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1.校验  账号 , 密码 , 确认密码不能为空
        // 1.1 账号
        ThrowUtils.throwIf(StrUtil.isBlank(userAccount), ErrorCode.PARAMS_ERROR, "账号为空!");
        //1.2 密码
        ThrowUtils.throwIf(StrUtil.isBlank(userPassword), ErrorCode.PARAMS_ERROR, "密码为空!");
        //2.1 用户名和密码长度必须满足条件 用户名大于4 , 密码大于6
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "非法的账号长度!");
        ThrowUtils.throwIf(userPassword.length() < 6, ErrorCode.PARAMS_ERROR, "非法的密码长度!");

        // 2.查询用户是否存在
        //2.1 获取加密的密码
        String encrypt = doPasswordEncryption(userPassword);

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encrypt);
        User user = this.baseMapper.selectOne(queryWrapper);
        /*ThrowUtils.throwIf(user == null,ErrorCode.PARAMS_ERROR,"用户名或密码错误!");*/
        if (user == null) {
            log.info("user login failed, due to account or password error!");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, " 账号或密码错误!");
        }
        // 3.记录用户的登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATUS, user);
        // 生成VO并返回
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前的登录用户 , 系统内部调用
     * @param request 请求
     * @return User
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        User currentUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATUS);
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "当前没有用户登录");
        }
        // 从数据库查询 ,保证数据是最新的
        Long userId = currentUser.getId();
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return user;
    }


    /**
     * 对用户密码进行加密
     *
     * @param password 原来的明文密码
     * @return 加密的密码
     */
    @Override
    public String doPasswordEncryption(String password) {
        //加盐混淆
        return DigestUtils.md5DigestAsHex((password + UserConstant.SALT).getBytes());
    }

    /**
     * 获取用户登录后的用户信息VO
     *
     * @param user 用户
     * @return 脱敏的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if(user == null){
            return null;
        }
        LoginUserVO loginUserVo = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVo);
        return loginUserVo;
    }

    /**
     * 用户注销
     * @param request 注销 -> 退出登录
     * @return Boolean
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先获取用户, 看看用户有没有登录
        Object object = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATUS);
        User user = (User) object;
        if (user == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"当前没有用户登录!");
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATUS);
        return true;
    }

    /**
     * 把User转成UserVO
     *
     * @param user 原信息
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> users) {
        if (CollUtil.isEmpty(users)) {
            return new ArrayList<>();
        }
        List<UserVO> userVOList = new ArrayList<>();
        for (User user : users) {
            UserVO userVO = this.getUserVO(user);
            BeanUtil.copyProperties(user, userVO);
            userVOList.add(userVO);
        }
        return userVOList;
    }

    /**
     * @param userQueryRequest 查询请求体
     * @return
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        //构造wrapper, 首先获取参数
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        //如果参数不是空, 传入参数
        queryWrapper.eq(ObjUtil.isNotNull(id),"id",id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole),"userRole",userRole);
        queryWrapper.like(StrUtil.isNotBlank(userName),"userName",userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount),"userAccount",userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile),"userProfile",userProfile);
        //排序方式
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField),sortOrder.equals("ascend"),sortField);

        //返回
        return queryWrapper;
    }
}




