package com.lumine3.luminapicturebackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 空间编辑请求类 ->
 * 对用户使用
 * 仅可以修改名称
 */
@Data
public class SpaceEditRequest implements Serializable {

    private static final long serialVersionUID = -3477453530364515557L;
    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;


}
