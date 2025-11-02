package com.lumine3.luminapicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumine3.luminapicturebackend.annotation.AuthCheck;
import com.lumine3.luminapicturebackend.common.BaseResponse;
import com.lumine3.luminapicturebackend.common.DeleteRequest;
import com.lumine3.luminapicturebackend.common.PageRequest;
import com.lumine3.luminapicturebackend.common.ResultUtils;
import com.lumine3.luminapicturebackend.constant.UserConstant;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.model.dto.user.*;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.vo.LoginUserVO;
import com.lumine3.luminapicturebackend.model.vo.UserVO;
import com.lumine3.luminapicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户的接口
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;


    /**
     * 用户注册接口
     *
     * @param userRegisterRequest 用户注册请求
     * @return id
     */
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        //判断
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空!");
        //获取数据
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String confirmPassword = userRegisterRequest.getConfirmPassword();
        long register = userService.userRegister(userAccount, userPassword, confirmPassword);
        return ResultUtils.success(register);
    }

    /**
     * 用户登录接口
     *
     * @param userLoginRequest 用户登录请求
     * @return 脱敏后的用户信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //判断为空?
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空!");
        //获取数据
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVo = userService.userLogin(userAccount, userPassword,request);
        return ResultUtils.success(loginUserVo);
    }


    /**
     * 获取当前登录用户
     * @param request 请求
     * @return VO
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        LoginUserVO loginUserVO = userService.getLoginUserVO(loginUser);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request){
        boolean res = userService.userLogout(request);
        return ResultUtils.success(res);
    }


    /**
     * 创建用户, 由管理员直接创建
     * @param
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) //管理员权限
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        //判断
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空!");
        //获取数据
        User user = new User();
        BeanUtil.copyProperties(userAddRequest,user);
        //填充默认密码
        String defaultPassword = userService.doPasswordEncryption(UserConstant.DEFAULT_PASSWORD);
        user.setUserPassword(defaultPassword);
        boolean saved = userService.save(user);
        ThrowUtils.throwIf(!saved,ErrorCode.OPERATION_ERROR,"数据库错误, 新建用户失败!");
        return ResultUtils.success(user.getId());
    }


    /**
     * 依照 id 获取用户
     * 仅管理员可以操作
     * @param id 用户id
     * @return User
     * */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get")
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0,ErrorCode.PARAMS_ERROR,"参数错误!");
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null ,ErrorCode.NOT_FOUND_ERROR,"未找到数据!");
        return ResultUtils.success(user);
    }

    /**
     * 根据id获取用户VO 对于普通用户
     * @param id
     * @return
     */
    @GetMapping("/user/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        UserVO userVO = userService.getUserVO(user);
        return ResultUtils.success(userVO);
    }


    /**
     * 删除用户 只能管理员进行
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if(deleteRequest == null || deleteRequest.getId() <= 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean removed = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(removed);
    }


    /**
     * 更新用户 -> 管理员进行
     * @param userUpdateRequest
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if(userUpdateRequest == null || userUpdateRequest.getId() == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest,user);
        boolean updated = userService.updateById(user);
        ThrowUtils.throwIf(!updated,ErrorCode.OPERATION_ERROR,"更新用户失败!");
        return ResultUtils.success(updated);
    }


    /**
     * 分页查询
     *
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserVO>> listUserPageVO(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);

        //当前的页号和页大小
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        Page<User> page = userService.page(new Page<>(current, pageSize),userService.getQueryWrapper(userQueryRequest));
        //转换成VO
        Page<UserVO> userVOPage = new Page<>(current,pageSize, page.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(page.getRecords());
        //设置分页类
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }





}
