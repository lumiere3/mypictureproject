package com.lumine3.luminapicturebackend.controller;

import com.lumine3.luminapicturebackend.annotation.AuthCheck;
import com.lumine3.luminapicturebackend.common.BaseResponse;
import com.lumine3.luminapicturebackend.common.ResultUtils;
import com.lumine3.luminapicturebackend.constant.UserConstant;
import com.lumine3.luminapicturebackend.model.dto.picture.PictureUploadRequest;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.vo.PictureVO;
import com.lumine3.luminapicturebackend.service.PictureService;
import com.lumine3.luminapicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;


    /**
     * 上传图片 -> 目前阶段只有管理员可以
     *
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestParam("file") MultipartFile file
            , PictureUploadRequest pictureUploadRequest
            , HttpServletRequest request
    ) {
        //获取登录用户
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(file, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

}
