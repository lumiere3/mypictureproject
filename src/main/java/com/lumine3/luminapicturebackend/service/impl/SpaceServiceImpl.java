package com.lumine3.luminapicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumine3.luminapicturebackend.constant.SpaceConstant;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import com.lumine3.luminapicturebackend.exception.ThrowUtils;
import com.lumine3.luminapicturebackend.model.dto.space.SpaceAddRequest;
import com.lumine3.luminapicturebackend.model.dto.space.SpaceQueryRequest;
import com.lumine3.luminapicturebackend.model.entity.Space;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.enums.SpaceLevelEnum;
import com.lumine3.luminapicturebackend.model.vo.SpaceVO;
import com.lumine3.luminapicturebackend.model.vo.UserVO;
import com.lumine3.luminapicturebackend.service.SpaceService;
import com.lumine3.luminapicturebackend.mapper.SpaceMapper;
import com.lumine3.luminapicturebackend.service.UserService;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @author Asus
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-11-21 16:00:26
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    // 锁优化 add方法
    private Map<Long,Object> lockMap = new ConcurrentHashMap<>();

    /**
     * 校验空间的合法性
     *
     * @param isAdd 是否是新增空间
     * @param space 空间对象
     */
    @Override
    public void validSpace(Space space, boolean isAdd) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        //取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum enumByValue = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 如果是新增/创建空间
        if (isAdd) {
            //必须有空间名称
            if (StrUtil.isEmpty(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空!");
            }
            // 空间等级必须正确
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间等级不能为空!");
            }
        }
        // 修改数据
        if (spaceLevel == null && enumByValue == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "错误的空间等级!");
        }
        ThrowUtils.throwIf((StrUtil.isNotBlank(spaceName) && spaceName.length() > 30)
                , ErrorCode.PARAMS_ERROR
                , "空间名称过长");
    }

    /**
     * 获取Space的封装返回类
     *
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        //转换
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联信息
        Long userId = spaceVO.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOs(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .collect(Collectors.toSet());
        // 映射 userId 和 用户
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        // 根据查询的请求信息, 构造wrapper
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        //为空直接返回
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象里面取值, allget获取所有字段
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        // 拼接查询条件


        // 多字段搜索, 利用and来实现, 拼接我们需要的查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }


    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 用户创建自己的私有空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    /*@Transactional*/
    /**
     * 使用注解事务时, 我们的锁释放的时候, 其实事务并没有提交,
     * 如果这个时候别的线程去查询, 可能还是会查不到刚刚的数据
     */

    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 填充默认的参数
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest,space);
        // 如果以下的参数不存在, 设置一个默认的名称
        if(StrUtil.isBlank(space.getSpaceName())){
            space.setSpaceName(SpaceConstant.SPACE_DEFAULT_NAME);
        }
        if(space.getSpaceLevel() == null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 根据空间的级别填充空间的容量
        this.fillSpaceBySpaceLevel(space);
        // 参数校验
        this.validSpace(space,true);
        // 获取权限, 非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);

        boolean admin = userService.isAdmin(loginUser);
        // 如果普通用户创建非普通级别的空间, 是不被允许的
        if(SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !admin){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "权限不足, 无法创建高级别的空间");
        }

        // 一个用户只能创建一个空间, 为了避免并发问题, 我们一个加锁
        Object lock = lockMap.computeIfAbsent(userId,key -> new Object());
       // String lock = String.valueOf(userId).intern();
        synchronized (lock){
            try{
                // 创建事务
                Long spaceId = transactionTemplate.execute(status -> {
                    // 判断空间是否存在, 如果存在就创建, 否则不能创建
                    // 看看当前的用户是否创建了
                    boolean exists = this.lambdaQuery()
                            .eq(Space::getUserId, userId)
                            .exists();
                    ThrowUtils.throwIf(exists,ErrorCode.OPERATION_ERROR,"每个用户仅能创建一个个人空间!");
                    //不存在, 创建
                    boolean saved = this.save(space);
                    ThrowUtils.throwIf(!saved,ErrorCode.OPERATION_ERROR,"数据库操作异常,空间保存失败!");
                    return space.getId();
                });
                return Optional.ofNullable(spaceId).orElse(-1L);
            }finally {
                // 释放锁, 防止内存泄漏
                lockMap.remove(userId);
            }
        }
    }
}




