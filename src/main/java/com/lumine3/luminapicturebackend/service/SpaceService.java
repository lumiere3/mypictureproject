package com.lumine3.luminapicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumine3.luminapicturebackend.model.dto.space.SpaceAddRequest;
import com.lumine3.luminapicturebackend.model.dto.space.SpaceQueryRequest;
import com.lumine3.luminapicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumine3.luminapicturebackend.model.entity.User;
import com.lumine3.luminapicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Asus
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2025-11-21 16:00:26
 */
public interface SpaceService extends IService<Space> {

    /**
     * 校验空间是否规范合法
     *
     * @param space
     */
    void validSpace(Space space, boolean isAdd);

    /**
     * 获取Space的封装返回类
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);


    /**
     * 获取多条封装类
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOs(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取封装的查询对象
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 自动填充
     *
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);


    /**
     * 用户创建私有空间
     *
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);
}
