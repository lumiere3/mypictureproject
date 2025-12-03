package com.lumine3.luminapicturebackend.api.imagesearch;

import com.lumine3.luminapicturebackend.api.imagesearch.model.ImageSearchResult;
import com.lumine3.luminapicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.lumine3.luminapicturebackend.api.imagesearch.sub.GetImageListApi;
import com.lumine3.luminapicturebackend.api.imagesearch.sub.GetImagePageApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {

    /**
     * 以图搜图
     * @param oriUrl 需要搜索的图片的url
     * @return 相似结果的集合
     */
    public static List<ImageSearchResult> getImageSearchResults(String oriUrl) {
        String imagePageUrl = GetImagePageApi.getImagePageUrl(oriUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        return GetImageListApi.getImageList(imageFirstUrl);
    }
}
