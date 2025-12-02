package com.lumine3.luminapicturebackend.api.imagesearch.model;

import lombok.Data;

/**
 * 我们设置的以图搜图功能，这个类用来接收结果（API的返回值）
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}
