package com.lumine3.luminapicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 以图搜图
 */

@Data
public class SearchPictureByPictureRequest implements Serializable {

    private static final long serialVersionUID = -4396181815884268617L;
    /**
     * 图片 id
     */
    private Long pictureId;

}
