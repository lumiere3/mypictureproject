package com.lumine3.luminapicturebackend.controller;

import com.lumine3.luminapicturebackend.annotation.AuthCheck;
import com.lumine3.luminapicturebackend.common.BaseResponse;
import com.lumine3.luminapicturebackend.common.ResultUtils;
import com.lumine3.luminapicturebackend.constant.UserConstant;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.manager.COSManager;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private COSManager cosManager;

    /**
     * 文件上传接口, 测试使用
     * @return 文件的路径
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/test/upload")
    public BaseResponse<String> testFileUpload(@RequestPart("file") MultipartFile file) {
        // 指定文件名和文件目录
        String fileName = file.getOriginalFilename();
        String filePath = String.format("/test/%s", fileName);
        // 文件
        File currentFile = null;
        try {
            currentFile = File.createTempFile(filePath,null);
            file.transferTo(currentFile);
            cosManager.putObject(filePath,currentFile);
            // 返回一个文件的地址
            return ResultUtils.success(filePath);
        } catch (IOException e) {
            log.error("error : upload fail ,{}",filePath + "and e : " + e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文件上传失败!");
        }finally {
            //清除本地的临时文件
            if(currentFile != null){
                boolean delete = currentFile.delete();
                if(!delete){
                    log.error("error : current file delete fail ,{}",currentFile.getAbsolutePath());
                }
            }
        }

    }

    /**
     * 测试文件下载
     *
     * @param filepath 文件路径
     * @param response 响应对象
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download/")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        COSObjectInputStream cosObjectInput = null;
        try {
            COSObject cosObject = cosManager.getObject(filepath);
            cosObjectInput = cosObject.getObjectContent();
            // 处理下载到的流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);
            // 写入响应
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            if (cosObjectInput != null) {
                cosObjectInput.close();
            }
        }
    }


}
