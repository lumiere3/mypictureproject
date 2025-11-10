package com.lumine3.luminapicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 标签列表
 */
@Data
public class PictureTagCategory implements Serializable {

    private static final long serialVersionUID = -6315942344369637062L;
    private List<String> tagList;
    private List<String> categoryList;
}
