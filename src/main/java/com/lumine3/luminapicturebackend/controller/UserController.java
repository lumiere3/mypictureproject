package com.lumine3.luminapicturebackend.controller;

import com.lumine3.luminapicturebackend.common.BaseResponse;
import com.lumine3.luminapicturebackend.common.ResultUtils;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.model.dto.UserLoginRequest;
import com.lumine3.luminapicturebackend.model.dto.UserRegisterRequest;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.vo.LoginUserVO;
import com.lumine3.luminapicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

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
}
