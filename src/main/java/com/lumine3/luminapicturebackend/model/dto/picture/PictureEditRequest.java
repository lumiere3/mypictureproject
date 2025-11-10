package com.lumine3.luminapicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 编辑图片 -> 给普通用户使用
 */
@Data
public class PictureEditRequest implements Serializable {

    private static final long serialVersionUID = 2368252146494511564L;
    /**
     * id
     */
    private Long id;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tags;

}
