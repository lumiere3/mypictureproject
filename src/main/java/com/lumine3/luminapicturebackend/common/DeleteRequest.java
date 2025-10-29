package com.lumine3.luminapicturebackend.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

}
