package com.lumine3.luminapicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间更新请求类
 * 对管理员使用
 * 修改的范围更大 例如级别和容量都可以修改
 */
@Data
public class SpaceUpdateRequest implements Serializable {

    private static final long serialVersionUID = -5997255236874800422L;
    /**
     * id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;


}
