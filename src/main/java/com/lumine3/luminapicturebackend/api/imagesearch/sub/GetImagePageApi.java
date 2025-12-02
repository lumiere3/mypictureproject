package com.lumine3.luminapicturebackend.api.imagesearch.sub;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.lumine3.luminapicturebackend.exception.BusinessException;
import com.lumine3.luminapicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


/**
 * 获取以图搜图的url
 */
@Slf4j
public class GetImagePageApi {

    /**
     * 获取以图搜图的url
     * 我们这里通过百度搜图来实现 -> 即我们构造一个请求，传入我们的图片来进行以图搜图
     * @param url url of our picture
     */
    public static String getImagePageUrl(String url) {
        // 我们发现搜图的请求参数是一个表单，所以我们第一步就是构造这个表单
        /** 表单样式如下：
         * image https%3A%2F%2Fi0.hdslb.com%2Fbfs%2Farticle%2Ff4092956ecc6673f5bf2e05851b8c45558af7564.png
         * tn pc
         * from pc
         * image_source PC_UPLOAD_URL
         * sdkParams undefined
         */
        Map<String, Object> form = new HashMap<>();
        form.put("image", url);
        form.put("tn", "pc");
        form.put("from","pc");
        form.put("image_source","PC_UPLOAD_URL");
        // 还有一个 uptime 1764680958959
        long uptime = System.currentTimeMillis();
        //构造请求地址
        String postUrl = String.format("https://graph.baidu.com/upload?uptime=%s",uptime);
        // 发送请求并获取结果
        try{
            HttpResponse httpResponse = HttpRequest
                    .post(postUrl)
                    .header("acs-token",RandomUtil.randomString(1)) // 这里需要指定acs-token 不然会响应系统异常
                    .form(form)
                    .timeout(5000)
                    .execute();
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"调用以图搜图接口失败! 未获取响应");
            }
            // 解析请求的响应结果
            String body = httpResponse.body();
            //转换成Map
            Map<String,Object> bean = JSONUtil.toBean(body, Map.class);
            if (bean == null || !Integer.valueOf(0).equals(bean.get("status"))){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"调用以图搜图接口失败! 未获取响应或响应异常");
            }
            // 获取data
            Map<String,Object> data = (Map<String, Object>) bean.get("data");
            String rawUrl = (String) data.get("url");
            // 进行解码
            String urlRes = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // url 不能为空
            if (urlRes == null || urlRes.isEmpty()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"调用以图搜图接口失败! 没有返回结果");
            }
            return urlRes;
        }catch (Exception e){
            log.error("调用百度试图接口失败!",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"调用以图搜图接口失败!");
        }
    }

    public static void main(String[] args) {
        String url = "https://i0.hdslb.com/bfs/article/f4092956ecc6673f5bf2e05851b8c45558af7564.png";
        String imagePageUrl = getImagePageUrl(url);
        System.out.println(imagePageUrl);
    }
}
