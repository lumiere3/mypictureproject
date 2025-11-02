package com.lumine3.luminapicturebackend.aop;


import cn.hutool.core.util.StrUtil;
import com.lumine3.luminapicturebackend.annotation.AuthCheck;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.enums.UserRoleEnum;
import com.lumine3.luminapicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 用于权限校验
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 设置切点
     *
     * @param joinPoint 切入点
     * @param authCheck
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        /** 获取请求*/
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        /** 转换一下  获得 httpRequest  */
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        //获取当前登录用户信息
        User loginUser = userService.getLoginUser(request);
        //校验登录用户的权限
        UserRoleEnum roleEnum = UserRoleEnum.getEnumByValue(mustRole);
        if (roleEnum == null) {
            //表示必须有的权限为无, 就是不需要权限, 那么直接放行
            return joinPoint.proceed();
        }
        /** 到了这个位置, 表示当前的用户必须权限匹配才可以通行
         * 我们先获取用户的权限的枚举
         **/
        String userRole = loginUser.getUserRole();
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //* 分情况进行
        if (roleEnum.equals(UserRoleEnum.ADMIN)) {
            //表示当前需要管理员权限, 如果用户没有权限, 那么就不能放行
            if (!userRoleEnum.equals(UserRoleEnum.ADMIN)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        //通过放行
        return joinPoint.proceed();
    }

}

