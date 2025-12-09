package com.lumine3.luminapicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.lumine3.luminapicturebackend.annotation.AuthCheck;
import com.lumine3.luminapicturebackend.api.imagesearch.ImageSearchApiFacade;
import com.lumine3.luminapicturebackend.api.imagesearch.model.ImageSearchResult;
import com.lumine3.luminapicturebackend.common.BaseResponse;
import com.lumine3.luminapicturebackend.common.DeleteRequest;
import com.lumine3.luminapicturebackend.common.ResultUtils;
import com.lumine3.luminapicturebackend.constant.PictureConstant;
import com.lumine3.luminapicturebackend.constant.UserConstant;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.model.dto.picture.*;
import com.lumine3.luminapicturebackend.model.entity.Picture;
import com.lumine3.luminapicturebackend.model.entity.Space;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.enums.PictureReviewStatusEnum;
import com.lumine3.luminapicturebackend.model.vo.PictureTagCategory;
import com.lumine3.luminapicturebackend.model.vo.PictureVO;
import com.lumine3.luminapicturebackend.model.vo.UserAvatarVO;
import com.lumine3.luminapicturebackend.service.PictureService;
import com.lumine3.luminapicturebackend.service.SpaceService;
import com.lumine3.luminapicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    // 引入Redis
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 构建本地缓存 caffeine实现 为了方便直接在controller里面使用
     */
    @Resource
    private Cache<String,String> localCacheByCaffeine;

    /**
     * 上传图片
     *
     * @param file
     * @return
     */
    @PostMapping("/upload")
    /*@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)*/
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

    /**
     * 上传图片  -> 下载支持使用url来上传
     *
     * @param pictureUploadRequest
     * @return
     */
    @PostMapping("/upload/url")
    /*@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)*/
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest
            , HttpServletRequest request
    ) {
        //获取登录用户
        User loginUser = userService.getLoginUser(request);
        //获取url
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        pictureService.cleanHomePageCache();
        return ResultUtils.success(pictureVO);
    }



    /**
     * 删除图片
     * @param
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 权限校验, 这里应该是两种情况: 1.管理员 -> 可以删除 2.图片的上传者 -> 可以删除
        User loginUser = userService.getLoginUser(request);
        long pictureId = deleteRequest.getId();
        pictureService.deletePicture(pictureId, loginUser);
        return ResultUtils.success(true);
       /* //查询当前图片
        Picture oldPicture = pictureService.getById(pictureId);
       //图片必须存在
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 没有登录肯定不行
        ThrowUtils.throwIf(loginUser == null ,ErrorCode.NO_AUTH_ERROR ,"用户未登录, 无法删除!");
        // 校验权限
        pictureService.checkPictureAuth(loginUser,oldPicture);
        //校验完成, 删除图片
        boolean removed = pictureService.removeById(pictureId);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR);
        // 清理COS的图片资源
        pictureService.clearPictureFile(oldPicture);
        //清理缓存
        pictureService.cleanHomePageCache();*/
    }


    /**
     * 更新图片（仅管理员可用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(oldPicture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        pictureService.cleanHomePageCache();
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        //校验权限 -> 如果不是公共空间
        Long spaceId = picture.getSpaceId();
        if (spaceId != null) {
            pictureService.checkPictureAuth(userService.getLoginUser(request),picture);
        }
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            //公开图库
            // 用户只能看到审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else{
            // 私有的图库就需要校验权限了
            User loginUser = userService.getLoginUser(request);
            //空间必须存在
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在!");
            if (!Objects.equals(loginUser.getId(), space.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有访问当前空间的权限!");
            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取图片列表（封装类） -> 这里使用了缓存
     * 我们使用多级缓存进行 包括本地缓存caffeine 和 Redis 缓存
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 用户只能看到审核通过的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 引入了缓存, 因此我们在查询数据库之前, 应该前尝试从缓存获取  请求 -> 本地缓存 -> redis -> 数据库
        // 构建缓存的key + 注意需要加入查询条件 -> 我们需要把查询条件序列化
        String queryJSON = JSONUtil.toJsonStr(pictureQueryRequest);
        //json可能长度很长 , 以此需要进行转化 使用md5
        String hashKey = DigestUtils.md5DigestAsHex(queryJSON.getBytes());
        // 拼接成为key
         /*= String.format("lumina-picture:listPictureVOByPage:%s", hashKey);*/
        String cacheKey = String.format("%s%s", PictureConstant.HOME_PAGE_CACHE,hashKey);
        //1 先从本地缓存里面获取
        String cachedValue = localCacheByCaffeine.getIfPresent(cacheKey);
        if (cachedValue != null) { //如果本地缓存存在
            // 把value转成对象返回
            Page<PictureVO> cacheBean = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cacheBean);
        }
        //2. 再去Redis分布式缓存里面获取
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        // 如果存在, 先更新本地缓存 让后返回
        if (cachedValue != null) {
            // 更新本地缓存
            localCacheByCaffeine.put(cacheKey, cachedValue);
            Page<PictureVO> cacheBean = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cacheBean);
        }
        // 两级缓存都不存在就查询数据库
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            //公开图库
            // 用户只能看到审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        }else{
            // 私有的图库就需要校验权限了
            User loginUser = userService.getLoginUser(request);
            //空间必须存在
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在!");
            if (!Objects.equals(loginUser.getId(), space.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有访问当前空间的权限!");
            }
        }
        //3. 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        //写入缓存 key我们已经构建过了, 现在只需要构造value
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        //设置过期时间 5-10分钟随机
        int expireTime = 300 + RandomUtil.randomInt(0,300);
        //4. 写入缓存, 此时需要同时写入caffeine和redis
        opsForValue.set(cacheKey, cacheValue, expireTime, TimeUnit.SECONDS);
        localCacheByCaffeine.put(cacheKey,cacheValue);
        return ResultUtils.success(pictureVOPage);
    }


    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest,loginUser);
        return ResultUtils.success(true);
       /* // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        //补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        *//*if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }*//*
        pictureService.checkPictureAuth(loginUser, oldPicture);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        //清理主页缓存
        pictureService.cleanHomePageCache();*/
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "游戏", "创意","测试");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "壁纸","测试");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }


    /**
     *  获取用户上传的头像的url
      */
    @PostMapping("/getAvatar")
    public BaseResponse<UserAvatarVO> getUserAvatar(MultipartFile file, HttpServletRequest request) {
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        UserAvatarVO userAvatarVO = new UserAvatarVO();
        String userAvatar = pictureService.getUserAvatar(file,loginUser);
        userAvatarVO.setUserAvatar(userAvatar);
        return ResultUtils.success(userAvatarVO);
    }

    /**
     * 审核用户上传的图片 <--> 必须是管理员
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/review")
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request){
        // 检验数据
        if (pictureReviewRequest == null || pictureReviewRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前的用户
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        pictureService.cleanHomePageCache();
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取图片
     * @return
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/upload/batch")
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                             HttpServletRequest request) {
        // 检验数据
        if (pictureUploadByBatchRequest == null ) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前用户
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        pictureService.cleanHomePageCache();
        return ResultUtils.success(uploadCount);
    }


    /**
     * 以图搜图
     * @param searchPictureByPictureRequest
     * @return
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest
                                                                       ){
        ThrowUtils.throwIf(searchPictureByPictureRequest == null,ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null,ErrorCode.PARAMS_ERROR);

        //查询当前图片
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null,ErrorCode.NOT_FOUND_ERROR);

        //获取图片的url
        String pictureUrl = picture.getUrl();

        //调用以图搜图API
        List<ImageSearchResult> imageSearchResults = ImageSearchApiFacade.getImageSearchResults(pictureUrl);
        return ResultUtils.success(imageSearchResults);
    }


    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                              HttpServletRequest request){
        ThrowUtils.throwIf(searchPictureByColorRequest == null,ErrorCode.PARAMS_ERROR);
        String color = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColorsInPrivate(spaceId, color, loginUser);
        return ResultUtils.success(pictureVOList);
    }


    /**
     * 批量编辑图片
     * @param pictureEditBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditBatchRequest == null,ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditBatchRequest, loginUser);
        return ResultUtils.success(true);
    }
}
