package com.lumine3.luminapicturebackend.manager;

import com.lumine3.luminapicturebackend.config.COSClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传至云端对象存储
 */
@Component
public class COSManager {

    @Resource
    private COSClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;


    // 将本地文件上传到 COS
    /**
     * 上传对象
     * @param key 上传文件的的位置
     * @param file 本地文件
     */
    public PutObjectResult putObject( String key, File file)
            throws CosClientException, CosServiceException{
        //创建桶, 我们就上传到现在的桶
        String bucketName = cosClientConfig.getBucket();
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }


    /**
     * 上传并解析图片
     */
    public PutObjectResult putPictureObject(String key, File file)
            throws CosClientException, CosServiceException {
        //创建桶, 我们就上传到现在的桶
        String bucketName = cosClientConfig.getBucket();
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file);
        //我们添加图片处理规则
        //  图片操作规则
        PicOperations picOperations = new PicOperations();
        // isPicInfo  是否返回原图信息，0不返回原图信息，1返回原图信息，默认为0
        // rules  处理规则，一条规则对应一个处理结果（目前支持五条规则），不填则不进行图片处理
        picOperations.setIsPicInfo(1);
       /* // 使用一个集合表示操作的rules 如果需要的话
        List<PicOperations.Rule> ruleList = new ArrayList<>();
        // 添加每一条规则
        PicOperations.Rule rule1 = new PicOperations.Rule();
        */
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }


}
