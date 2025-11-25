package com.lumine3.luminapicturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 空间的等级
 */
@Data
@AllArgsConstructor
public class SpaceLevel {
    /**
     * 空间的等级信息
     */

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}
