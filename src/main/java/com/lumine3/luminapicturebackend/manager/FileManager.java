package com.lumine3.luminapicturebackend.manager;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.lumine3.luminapicturebackend.config.COSClientConfig;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * 已废弃 -> 使用upload包下的接口
 */
@Deprecated
@Service
@Slf4j
public class FileManager {

    @Resource
    private COSClientConfig cosClientConfig;

    @Resource
    private COSManager cosManager;

    // .. 解析图片并且上传
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验文件
        validPicture(multipartFile);
        // 获取文件名, 和文件的上传前缀拼接 ,文件名相同那不一定是同一个文件, 我们设置一个随机的前缀
        String uuid = RandomUtil.randomString(16);
        String originalFilename = multipartFile.getOriginalFilename();
        // 为了防止原来文件名的字符不符合, 我们自己构建文件名
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),
                uuid,
                FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 有了上传地址, 上传文件
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //解析文件信息
            // 获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            //获取图片的相关信息
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();
            double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();

            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath); //设置url
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename)); //图片名称
            uploadPictureResult.setPicSize(FileUtil.size(file)); //图片文件大小
            uploadPictureResult.setPicWidth(width); //图片宽
            uploadPictureResult.setPicHeight(height);// 图片高
            uploadPictureResult.setPicScale(scale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            // 返回一个文件的地址
            return uploadPictureResult;
        } catch (IOException e) {
            log.error("error : upload picture fail due to ", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败!");
        } finally {
            //临时文件清理
            deleteTempFile(file);
        }
    }


    /**
     * 检验文件是否合法
     *
     * @return
     */
    private void validPicture(MultipartFile multipartFile) {
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
    /**
     * 检验文件是否合法 通过url
     *
     * @return
     */
    private void validPicture(String fileUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");

        try {
            // 1. 验证 URL 格式
            new URL(fileUrl); // 验证是否是合法的 URL
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }

        // 2. 校验 URL 协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");

        // 3. 发送 HEAD 请求以验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            // 未正常返回，无需执行其他判断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 4. 校验文件类型
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 5. 校验文件大小
            String contentLengthStr = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_MB = 2 * 1024 * 1024L; // 限制文件大小为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }


    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        //清除本地的临时文件
        if (file != null) {
            boolean delete = file.delete();
            if (!delete) {
                log.error("error : current file delete fail ,{}", file.getAbsolutePath());
            }
        }
    }


    //todo 新增的方法 - 原始方法
    /**
     *  通过.. 解析url 上传图片
     * @param fileUrl
     * @param uploadPathPrefix
     * @return
     */
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
        // 校验url
        // todo
        validPicture(fileUrl);
        // 获取文件名, 和文件的上传前缀拼接 ,文件名相同那不一定是同一个文件, 我们设置一个随机的前缀
        String uuid = RandomUtil.randomString(16);
        String originalFilename = FileUtil.mainName(fileUrl);
        // 为了防止原来文件名的字符不符合, 我们自己构建文件名
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),
                uuid,
                FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 有了上传地址, 上传文件
        File file = null;
        try {
            file = File.createTempFile(uploadPath, null);
            // todo
            // 新增下载文件到本地
            HttpUtil.downloadFile(fileUrl, file);
            //multipartFile.transferTo(file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //解析文件信息
            // 获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            //获取图片的相关信息
            int width = imageInfo.getWidth();
            int height = imageInfo.getHeight();
            double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();

            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath); //设置url
            uploadPictureResult.setPicName(FileUtil.mainName(originalFilename)); //图片名称
            uploadPictureResult.setPicSize(FileUtil.size(file)); //图片文件大小
            uploadPictureResult.setPicWidth(width); //图片宽
            uploadPictureResult.setPicHeight(height);// 图片高
            uploadPictureResult.setPicScale(scale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            // 返回一个文件的地址
            return uploadPictureResult;
        } catch (IOException e) {
            log.error("error : upload picture fail due to ", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败!");
        } finally {
            //临时文件清理
            deleteTempFile(file);
        }
    }

}
