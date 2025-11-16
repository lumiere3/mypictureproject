 package com.lumine3.luminapicturebackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.lumine3.luminapicturebackend.config.COSClientConfig;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.manager.COSManager;
import com.lumine3.luminapicturebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.extern.slf4j.Slf4j;


import javax.annotation.Resource;
import java.io.File;
import java.util.Date;



@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private COSClientConfig cosClientConfig;

    @Resource
    private COSManager cosManager;

    // .. 解析图片并且上传
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 校验文件
        validPicture(inputSource);
        // 获取文件名, 和文件的上传前缀拼接 ,文件名相同那不一定是同一个文件, 我们设置一个随机的前缀
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginalFilename(inputSource);
        // 为了防止原来文件名的字符不符合, 我们自己构建文件名
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),
                uuid,
                FileUtil.getSuffix(originalFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);
        // 有了上传地址, 上传文件
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源
            processFile(inputSource ,file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //解析文件信息
            // 获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            // 5. 封装返回结果
            return buildResult(originalFilename, file, uploadPath, imageInfo);
        } catch (Exception e) {
            log.error("error : upload picture fail due to ", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败!");
        } finally {
            //临时文件清理
            deleteTempFile(file);
        }
    }



    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginalFilename(Object inputSource);

    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);




    /**
     * 封装返回结果
     */
    private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        //获取图片的相关信息
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename)); //图片名称
        uploadPictureResult.setPicWidth(picWidth); //图片宽
        uploadPictureResult.setPicHeight(picHeight); // 图片高
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));  //图片文件大小
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath); //设置url
        return uploadPictureResult;
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
}
