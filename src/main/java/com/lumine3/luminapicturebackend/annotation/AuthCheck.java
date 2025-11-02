package com.lumine3.luminapicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//注解是针对方法的
@Target(ElementType.METHOD)
//注解生效的时刻 , 这里是运行时生效
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须的角色
     * 用于校验用户的权限, 即必须是拥有某个权限才可以调用这个接口
     */
    String mustRole() default "";
}
