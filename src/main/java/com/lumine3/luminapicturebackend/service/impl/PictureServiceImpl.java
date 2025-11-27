package com.lumine3.luminapicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.lumine3.luminapicturebackend.constant.PictureConstant;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.manager.COSManager;
import com.lumine3.luminapicturebackend.manager.FileManager;
import com.lumine3.luminapicturebackend.manager.upload.FilePictureUpload;
import com.lumine3.luminapicturebackend.manager.upload.PictureUploadTemplate;
import com.lumine3.luminapicturebackend.manager.upload.UrlPictureUpload;
import com.lumine3.luminapicturebackend.model.dto.picture.*;
import com.lumine3.luminapicturebackend.model.dto.file.UploadPictureResult;
import com.lumine3.luminapicturebackend.model.entity.Picture;
import com.lumine3.luminapicturebackend.model.entity.Space;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.enums.PictureReviewStatusEnum;
import com.lumine3.luminapicturebackend.model.vo.PictureVO;
import com.lumine3.luminapicturebackend.model.vo.UserVO;
import com.lumine3.luminapicturebackend.service.PictureService;
import com.lumine3.luminapicturebackend.mapper.PictureMapper;
import com.lumine3.luminapicturebackend.service.SpaceService;
import com.lumine3.luminapicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author Asus
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-11-05 20:11:30
*/
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    @Deprecated
    private FileManager fileManager;

    @Resource
    private COSManager cosManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private SpaceService spaceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private Cache<String, String> localCacheByCaffeine;

    //事务
    @Resource
    private TransactionTemplate transactionTemplate;
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
        // 空间校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            // 判断空间是否存在
            Space curSpace = spaceService.getById(spaceId);
            if (curSpace == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"未找到当前空间!");
            }
            //权限校验
            Long spaceUserId = curSpace.getUserId();
            ThrowUtils.throwIf(!Objects.equals(spaceUserId, loginUser.getId()),
                    ErrorCode.NO_AUTH_ERROR,"当前上传的空间不属于该用户! ");
            //空间额度校验
            if(curSpace.getTotalCount() >= curSpace.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间容量已达到最大图片数!");
            }
            if(curSpace.getTotalSize() >= curSpace.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间总容量不足!");
            }
        }
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
            // 当前图片的id 必须 和 原来图片的id一致
            // 当前如果没有spaceId , 那么设置 spaceId 和 原图片的一样
            if (spaceId == null){
                spaceId = oldPicture.getSpaceId();
            }else{
                // 检验两次是否相等
                Long oldPictureSpaceId = oldPicture.getSpaceId();
                if(!Objects.equals(oldPictureSpaceId, spaceId)){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"图片所属空间必须相同!");
                }
            }
        }
        //上传图片, 获得图片信息 -> 如果spaceId存在, 应该上传至私有图库
        String uploadPathPrefix;
        if(spaceId == null){
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
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
        picture.setSpaceId(spaceId); //指定图片的所属空间, 如果为null表示公共空间
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 设置图片名称, 如果没有设置那么就按照返回的信息设置, 如果有就设置为请求里面传入的信息
        String picName = uploadPictureResult.getPicName();
        if(pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())){
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
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
        // 上传完成后, 我们还需要更新空间的额度
        // 2. 没有id就是新建
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean saved = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "图片新建或更新失败!");
            if (finalSpaceId != null) {
                boolean updated = spaceService.lambdaUpdate()// todo 更新额度
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!updated,ErrorCode.OPERATION_ERROR,"空间更新失败!");
            }
           return null;
        });

        // todo 更新操作 -> 本质就是上传新的图片来覆盖原来的
        // 所以这里也需要删除原来图片在COS里面的值
        cleanHomePageCache();
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
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();

        // 审核相关
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        // 日期相关

        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();

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
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");

        queryWrapper.ge(ObjectUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjectUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

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
     * 删除图片
     */
    @Override
    public void deletePicture(long pictureId, User loginUser){
        //查询当前图片
        Picture oldPicture = this.getById(pictureId);
        //图片必须存在
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 没有登录肯定不行
        ThrowUtils.throwIf(loginUser == null ,ErrorCode.NO_AUTH_ERROR ,"用户未登录, 无法删除!");
        // 校验权限
        checkPictureAuth(loginUser,oldPicture);
        // 开启事务
        Long spaceId = oldPicture.getSpaceId();
        transactionTemplate.execute(status -> {
            //校验完成, 删除图片
            boolean removed = this.removeById(pictureId);
            ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR,"删除图片失败!");
            // 如果spaceId存在, 表示正在操作私有图库, 我们还要更新个人空间的额度
            if(spaceId != null){
                // 删除额度
                boolean updated = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR,"更新空间额度失败!");
            }
            return null;
        });
        // 清理COS的图片资源
        clearPictureFile(oldPicture);
        //清理主页缓存
        cleanHomePageCache();
    }

    /**
     * 用户编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser){
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        //补充审核参数
        fillReviewParams(picture, loginUser);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        /*if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }*/
        checkPictureAuth(loginUser, oldPicture);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        //清理主页缓存
        cleanHomePageCache();
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

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //参数校验
        String searchText = pictureUploadByBatchRequest.getSearchText();
        //ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMS_ERROR,"搜索参数不能为空!");
        Integer count = pictureUploadByBatchRequest.getCount();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        ThrowUtils.throwIf(count >= PictureConstant.MAX_BATCH_SIZE, ErrorCode.PARAMS_ERROR,"抓取的图片数过多! 不能大于20条");
        // 我们通过搜索引擎来随机抓取  -> 这里就用bing搜索引擎
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        // 添加链接 抓取图片
        Document document;
        try{
            document = Jsoup.connect(fetchUrl).get();
        }catch (IOException e){
            log.error("fail pictures batch due to url connnect :" + e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"抓取图片失败: 获取页面失败!");
        }
        // 解析文档来获取图片
        Element div = document.getElementsByClass("dgControl").first();
        ThrowUtils.throwIf(div == null, ErrorCode.OPERATION_ERROR,"获取元素失败");
        Elements imgElements = div.select("img.mimg");
        // 遍历每个元素, 上传图片
        // 记录上传成功的数量
        int uploadCount = 0;
        for (Element ele : imgElements) {
            // 取出图片的url
            String url = ele.attr("src");
            // 如果某一个url为空, 直接跳过
            if (StrUtil.isBlank(url)){
                log.info("current url is null ,skip" );
                continue;
            }
            // 处理url 防止存在一些字符无法上传 & ? 这种
            int index = url.indexOf("?");
            if (index > -1){
                url = url.substring(0, index);
            }
            //调用上传图片的方法上传
            // 设置图片的默认名称
            String picName = namePrefix + ( uploadCount+ 1  );

            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(url);
            pictureUploadRequest.setPicName(picName);

            try{
                PictureVO pictureVO = this.uploadPicture(url, pictureUploadRequest, loginUser);
                log.info("upload picture successfully, current pictureId is : " + pictureVO.getId());
                uploadCount++;
            }catch (Exception e){
                log.error("fail pictures batch due to upload by url" + e.getMessage());
                continue;
            }
            //如果数量已经达到设置的上传数量 , 直接返回
            if (uploadCount >= count){
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 从COS里面清理图片
     * 清理图片 -> 从COS里面清理文件
     * @param oldPicture 老图片
     */
    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        /*ThrowUtils.throwIf(oldPicture == null, ErrorCode.PARAMS_ERROR);*/
        // 首先判断, 这个图片的url有多少条被引用到
        String pictureUrl = oldPicture.getUrl();
        Long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 大于1 则说明不止一个地方用到了, 删除图片可能不合理
        if (count > 1){
            return;
        }
        try {
            // 否则, 我们删除COS里面的文件
            cosManager.deleteObject(pictureUrl);
            // 删除缩略图
            String thumbnailUrl = oldPicture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)){
                cosManager.deleteObject(thumbnailUrl);
            }
        } catch (MalformedURLException e) {
            log.error("fail clearPictureFile due to url connnect :" + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"错误的图片URL");
        }

    }

    /**
     * 清理主页的缓存 -> 包括本地 和 redis 缓存
     * 缓存的key已经确定
     */
    @Override
    public void cleanHomePageCache() {
        // 获取缓存 key的前缀
        String prefix = PictureConstant.HOME_PAGE_CACHE;
        // 清理本地缓存
        localCacheByCaffeine.invalidateAll();
        //清理Redis
        Set<String> keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }


    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }


}




