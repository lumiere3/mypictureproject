package com.lumine3.luminapicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间创建请求类
 */
@Data
public class SpaceAddRequest implements Serializable {

    private static final long serialVersionUID = 3801463063315515280L;
    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

}
