package com.lumine3.luminapicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumine3.luminapicturebackend.model.dto.picture.*;
import com.lumine3.luminapicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author Asus
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-11-05 20:11:30
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param inputSource        文件
     * @param pictureUploadRequest 平台结果
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);



    /**
     * 获取QueryWrapper
     *
     * @param pictureQueryRequest 图片搜索的请求包装类
     * @return QueryWrapper
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    void deletePicture(long pictureId, User loginUser);

    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 图片包装类 -> 单条
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 图片包装类 -> 列表
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 图片校验
     * @param picture
     */
    void validPicture(Picture picture);


    /**
     * 上传用户头像并获取url
     * @param multipartFile
     * @return
     */
    String getUserAvatar(MultipartFile multipartFile, User user);

    /**
     * 审核用户上传的图片
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User user);

    /**
     * 填充审核信息
     * @param picture
     * @param user
     */
    void fillReviewParams(Picture picture, User user);


    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );


    /**
     * 清理图片文件 --> 清理COS的图片文件
     *
     */
    void clearPictureFile(Picture oldPicture);

    /**
     * 清理主页缓存 包括本地缓存caffeine 和 redis 缓存
     *
     */
    void cleanHomePageCache();

    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 通过图片主色调查询图片
     * @param SpaceId
     * @param colors
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColorsInPrivate(Long SpaceId, String colors, User loginUser);
}
