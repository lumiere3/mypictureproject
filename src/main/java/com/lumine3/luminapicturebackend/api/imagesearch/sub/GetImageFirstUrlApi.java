package com.lumine3.luminapicturebackend.api.imagesearch.sub;

import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在getImagePageUrl里面我们得到了某张图片的百度识图的页面, 下面我们来获取识图结果的FirstUrl, 这里面含有我们需要的相似图片
 */
@Slf4j
public class GetImageFirstUrlApi {

    public static String getImageFirstUrl(String imageUrl) {
        /**
         * 获取firstUrl -> 获取这个url的html页面
         */
        try {

            Document document = Jsoup
                    .connect(imageUrl)
                    .timeout(5000)
                    .get();
            // 获取<script>标签
            Elements scripts = document.getElementsByTag("script");
            //找到包含 firstUrl 的内容
            for(Element script : scripts){
                String content = script.html();
                if(content.contains("\"firstUrl\"")){
                    // 正则表达式提取 firstUrl 的值
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        String firstUrl = matcher.group(1);
                        // 处理转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        return firstUrl;
                    }
                }
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未找到url");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"搜索失败！");
        }
    }


    public static void main(String[] args) {
        //请求目标url
        String url = "https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData[isLogoShow]=1&f=all&isLogoShow=1&session_id=16147356562523033091&sign=12155a4791269b80d109001764757446&tpl_from=pc";
        String imageFirstUrl = getImageFirstUrl(url);
        System.out.println("current: " + imageFirstUrl);
    }
}






