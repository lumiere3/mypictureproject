package com.lumine3.luminapicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传  -->
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }

    @Override
    protected String getOriginalFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 文件不能为空
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "图片上传参数不能为空!");
        // 图片的大小校验, 以M为单位, 我们设定不能超过3M
        long fileSize = multipartFile.getSize();
        final long M_UNIT = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > 3 * M_UNIT, ErrorCode.PARAMS_ERROR, "文件过大!");
        // 文件要是图片文件, 后缀检验.
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 后缀必须是图片格式
        final List<String> ALLOWED_FORMATS = Arrays.asList("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOWED_FORMATS.contains(suffix), ErrorCode.PARAMS_ERROR, "图片文件格式错误!");
    }
}
