package com.lumine3.luminapicturebackend.exception;

import com.lumine3.luminapicturebackend.common.BaseResponse;
import com.lumine3.luminapicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 运行时异常
     * @param e
     * @return
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        // 数据重复，违反唯一约束的异常
        if (e instanceof DuplicateKeyException) {
            log.error("DuplicateKeyException", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"系统错误: 数据重复!");
        }
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}

