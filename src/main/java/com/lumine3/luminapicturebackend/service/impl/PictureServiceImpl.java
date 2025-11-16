package com.lumine3.luminapicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.manager.FileManager;
import com.lumine3.luminapicturebackend.manager.upload.FilePictureUpload;
import com.lumine3.luminapicturebackend.manager.upload.PictureUploadTemplate;
import com.lumine3.luminapicturebackend.manager.upload.UrlPictureUpload;
import com.lumine3.luminapicturebackend.model.dto.picture.PictureQueryRequest;
import com.lumine3.luminapicturebackend.model.dto.file.UploadPictureResult;
import com.lumine3.luminapicturebackend.model.dto.picture.PictureReviewRequest;
import com.lumine3.luminapicturebackend.model.dto.picture.PictureUploadRequest;
import com.lumine3.luminapicturebackend.model.entity.Picture;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.enums.PictureReviewStatusEnum;
import com.lumine3.luminapicturebackend.model.vo.PictureVO;
import com.lumine3.luminapicturebackend.model.vo.UserVO;
import com.lumine3.luminapicturebackend.service.PictureService;
import com.lumine3.luminapicturebackend.mapper.PictureMapper;
import com.lumine3.luminapicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author Asus
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-11-05 20:11:30
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    /**
     * 文件上传接口
     *
     * @param inputSource        文件
     * @param pictureUploadRequest 平台结果
     * @param loginUser
     * @return VO
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(inputSource == null,ErrorCode.PARAMS_ERROR,"图片文件或url为空!");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新, 判断图片是否存在
        if (pictureId != null) {
            //现在也需要同时更改图片的审核状态
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在!");
            // 权限校验 -> 必须是管理员或者是图片的上传者本人才可以更新
            Long uploader = oldPicture.getUserId();
            if(!loginUser.getId().equals(uploader) && !userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有权限!无法更新图片!");
            }


        }
        //上传图片, 获得图片信息
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        // 根据inputSource的类型: 文件 或者 url 来区分上传的参数
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        // inputSource是字符串类型, 说明是一个url , 我们就使用url的方法
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 获取返回结果
        // 构造要入库的图片信息, 即Picture对象
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());

        //补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 保存图片到数据库 保存数据库的时候也要区分是更新还是新建, 以pictureId是否存在为判断条件
        // 1. 如果有id说明是更新
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 2. 没有id就是新建
        boolean saved = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "图片新建或更新失败!");
        // 构建返回VO
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取搜索的QueryWrapper
     *
     * @param pictureQueryRequest 图片搜索的请求包装类
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        // 根据查询的请求信息, 构造wrapper
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        //为空直接返回
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象里面取值, allget获取所有字段
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();

        Long reviewerId = pictureQueryRequest.getReviewerId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        // 多字段搜索, 利用and来实现, 拼接我们需要的查询条件
        if(StrUtil.isNotBlank(searchText)){
            // 拼接
            queryWrapper.and(
                    qw -> qw.like("name",searchText)
                    .or()
                    .like("introduction",searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


    /**
     * 获取图片的返回封装类 -> 针对单个图片
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());
        // 映射 userId 和 用户
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


    /**
     * 校验图片
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 获取用户上传的头像并且获取url
     * @param multipartFile
     * @return
     */
    @Override
    public String getUserAvatar(MultipartFile multipartFile ,User user) {
        //校验参数
        if (multipartFile == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (user == null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //上传图片, 获得图片信息
        String uploadPathPrefix = String.format("avatar/%s",user.getId());
        UploadPictureResult uploadPictureResult = filePictureUpload.uploadPicture(multipartFile, uploadPathPrefix);
        return uploadPictureResult.getUrl();
    }

    /**
     * 审核用户上传的图片
     * @param pictureReviewRequest
     * @param user
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User user) {
        //1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(user == null, ErrorCode.NO_AUTH_ERROR);
        // 获取请求信息
        Long id = pictureReviewRequest.getId();
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        //继续检验参数
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR);
        PictureReviewStatusEnum enums = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        // 参数不能为空
        ThrowUtils.throwIf((id == null || enums == null), ErrorCode.PARAMS_ERROR);
        // 不能把状态改回待审核 -> 因为用传入的图片默认就是 待审核 所以传入的参数要么通过, 要么拒绝
        ThrowUtils.throwIf(PictureReviewStatusEnum.REVIEWING.equals(enums), ErrorCode.PARAMS_ERROR, "非法的审核状态!");
        //2. 图片必须有, 图都没有审核啥?
        //2.1 查询图片, 图片必须存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //3. 审核不能重复
        ThrowUtils
                .throwIf(oldPicture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR,"请不要重复审核!");
        //4. 设置结果, 到了这一步就可以设置审核结果了
        /*oldPicture.setReviewMessage(reviewMessage);
        oldPicture.setReviewerId(user.getId()); 不要使用老的picture , 创建一个新的, 会更有效率, 因为是更新有值的字段
        老对象都有值, 相当于所以字段都更新了, 不如创建新的, 只更新需要的
        oldPicture.setReviewStatus(reviewStatus);*/
        Picture newPicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, newPicture);
        // 添加审核人 审核时间
        newPicture.setReviewerId(user.getId());
        newPicture.setReviewTime(new Date());
        boolean updated = this.updateById(newPicture);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 添加审核的相关字段 -> 管理员自动过审 -> 用户设置审核状态
     * @param picture
     * @param user
     */
    @Override
    public void fillReviewParams(Picture picture, User user) {
        ThrowUtils.throwIf(picture == null || user == null, ErrorCode.PARAMS_ERROR);
        //管理员自动过审
        boolean admin = userService.isAdmin(user);
        if (admin){
            picture.setReviewerId(user.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            // 非管理员操作, 添加和编辑都需要设置为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

}




