package com.lumine3.luminapicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片 -> 就是去从网上抓取
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {

    private static final long serialVersionUID = 8624067839522214756L;
    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 名称前缀
     */
    private String namePrefix;


    /**
     * 抓取数量
     */
    private Integer count = 5;
}
