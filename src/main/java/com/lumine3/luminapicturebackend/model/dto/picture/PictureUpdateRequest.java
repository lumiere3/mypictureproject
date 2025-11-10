package com.lumine3.luminapicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新请求, -> 给管理员使用
 */
@Data
public class PictureUpdateRequest implements Serializable {

    private static final long serialVersionUID = 7810324521427172504L;
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
