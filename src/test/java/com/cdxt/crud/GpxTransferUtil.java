package com.cdxt.crud;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 导出行者轨迹并上传到Strava
 *
 * @author zhuyongzhong
 * @date 2021/7/30 17:28
 */
@Slf4j
public class GpxTransferUtil {

    private static RestTemplate restTemplate = new RestTemplate();

    static {
        //设置编码格式，解决中文乱码
        restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    /******需要配置的参数开始*********/
    /**
     * 行者Cookie
     */
    private static final String XZ_COOKIE = "csrftoken=4F10CCPM5AlUMz2blPtLFn0GSr9qYejq; sessionid=pp4gah8ur9rqvkwu4ze13xats2qnixic; Hm_lvt_7b262f3838ed313bc65b9ec6316c79c4=1633913202,1633918260; Hm_lpvt_7b262f3838ed313bc65b9ec6316c79c4=1633918260";

    /**
     * Strava Cookie
     */
    private static final String STRAVA_COOKIE = "sp=cff8c7e2-368c-4513-a772-bdcfefde3e3e; _ga=GA1.2.194696465.1633855586; _gid=GA1.2.1359427665.1633855586; CloudFront-Policy=eyJTdGF0ZW1lbnQiOiBbeyJSZXNvdXJjZSI6Imh0dHBzOi8vaGVhdG1hcC1leHRlcm5hbC0qLnN0cmF2YS5jb20vKiIsIkNvbmRpdGlvbiI6eyJEYXRlTGVzc1RoYW4iOnsiQVdTOkVwb2NoVGltZSI6MTYzNDcyMDMwNn0sIkRhdGVHcmVhdGVyVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNjMzNDk2MzA2fX19XX0_; CloudFront-Key-Pair-Id=APKAIDPUN4QMG7VUQPSA; CloudFront-Signature=e2huHNS-AXTF-6cNoNlLg0buuKES9SYxxYrssvE01izhpDzygcbz-wNzsykg6l-ZN4fpXGBDp46nTiteVxtpmvA2W7WLfTRGNdAV14Q1j8JzK9biZpLJ-0D~lqWWWa71BG8Mjez98yLNLyw8v9CpfpGr45ih-NBPuIueiI4xioB1na02m57qEcLlq6oX~BN1limz2UwA7ycvz8HBEdOuf1NAPR1qeOraGFnm253RhVRkm2LA5ZLhak3xwXBuXdKgkPjf91ucCrX-IVLayQdI5MseYKrgG7GjT-Qxt26~QJRzcciAKZIJxvCLQhTG5PTkposibU5KFmM8aYHeI6zM1A__; _sp_ses.047d=*; _strava4_session=70t554spbq5cfg45cvcf2pdemuec9kp1; _dc_gtm_UA-6309847-24=1; _sp_id.047d=e5b74366-d673-4522-a823-f9291c3173ae.1633855575.3.1633916327.1633871356.20f3daae-46ec-487d-9df3-63708ad26c88";
    /******需要配置的参数结束*********/


    /**
     * 行者gpx 列表接口
     */
    private static final String XZ_GPX_LIST = "https://www.imxingzhe.com/api/v4/user_month_info?";
    /**
     * 行者个人信息
     */
    private static final String XZ_USER_INFO = "https://www.imxingzhe.com/api/v4/account/get_user_info/";
    /**
     * Strava轨迹上传地址
     */
    private static final String STRAVA_GPX_UPLOAD = "https://www.strava.com/upload/files";
    /**
     * Strava csrf token地址
     */
    private static final String STRAVA_CSRF_TOKEN_URL = "https://www.strava.com/upload/select";

    /**
     * 行者用户id
     */
    private static String XZ_USER_ID = null;
    /**
     * Strava CSRF TOKEN
     */
    private static String STRAVA_CSRF_TOKEN = null;


    @Test
    public void test2() {

        String dir = "d:/gpx";

        File folder = new File(dir);

        File[] files = folder.listFiles();

        for (File file : files) {
            String filePath = file.getAbsolutePath();
            uploadFile(filePath);
        }

    }


    @Test
    public void test1() {
        int startYear = 2010;
        int startMonth = 1;


        int endYear = 2021;
        int endMonth = 11;


        int m = startMonth;
        for (int y = startYear; y <= endYear; y++) {
            for (; ; ) {
                handleTransfer(y, m);
                if (m++ >= 12) {
                    m = 1;
                    break;
                }

                if (y == endYear && m > endMonth) {
                    return;
                }
            }
        }

    }


    /**
     * 传输数据
     *
     * @param year
     * @param month
     */
    private static void handleTransfer(int year, int month) {
        //获取轨迹列表
        List<String> ids = getGpxIdsList(year, month);

        for (String id : ids) {
            //下载单个轨迹
            String filePath = downLoadFile(id);
            //上传轨迹
            uploadFile(filePath);
        }

    }


    /**
     * 获取gpx id
     *
     * @param year
     * @param month
     */
    private static List<String> getGpxIdsList(int year, int month) {

        StringBuilder url = new StringBuilder(XZ_GPX_LIST);

        HashMap<String, Object> params = new HashMap<>();

        if (XZ_USER_ID == null) {
            XZ_USER_ID = getXZUserId();
        }

        params.put("user_id", XZ_USER_ID);
        params.put("year", year);
        params.put("month", month);

        //拼接参数
        Set<Map.Entry<String, Object>> entries = params.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            url.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }


        ResponseEntity<String> responseEntity = restTemplate.exchange(url.toString(), HttpMethod.GET, null, String.class);

        String body = responseEntity.getBody();
        JSONObject obj = JSON.parseObject(body);

        JSONArray arr = obj.getJSONObject("data").getJSONArray("wo_info");


        ArrayList<String> ids = new ArrayList<>();
        arr.forEach(o -> {
            JSONObject j = (JSONObject) o;
            String id = j.getString("id");
            ids.add(id);
        });


        log.info(String.format("轨迹列表：%s年%s月%s个>>>>>%s", year, month, ids.size(), StrUtil.join(",", ids)));
        return ids;
    }


    /**
     * 下载行者轨迹
     *
     * @param id
     */
    private static String downLoadFile(String id) {

        String url = String.format("https://www.imxingzhe.com/xing/%s/gpx/", id);


        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", XZ_COOKIE);


        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        log.info("gpx下载完成>>>>" + url);

        String body = exchange.getBody();

        //修正时区
        body = body.replaceAll("Z", "+0800");

        Document document = Jsoup.parse(body);

        Element element = document.selectFirst("name");

        String name = element.text();

        String fileName = String.format("D:/gpx/%s-%s.gpx", name, System.currentTimeMillis());

        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(fileName));
            out.write(body);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        log.info("保存成功>>>>" + fileName);
        return fileName;
    }


    /**
     * 上传到Strava
     *
     * @param filePath
     */
    private static void uploadFile(String filePath) {


        HttpHeaders headers = new HttpHeaders();
        headers.add("accept-language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.add("Cookie", STRAVA_COOKIE);


        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();


        FileSystemResource resource = new FileSystemResource(new File(filePath));

        //获取提交token
        if (STRAVA_CSRF_TOKEN == null) {
            STRAVA_CSRF_TOKEN = getStravaCsrfToken();
        }

        body.add("_method", "post");
        body.add("authenticity_token", STRAVA_CSRF_TOKEN);
        body.add("files[]", resource);


        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> exchange = restTemplate.exchange(STRAVA_GPX_UPLOAD, HttpMethod.POST, entity, String.class);

        String res = exchange.getBody();

        log.info("上传返回内容>>>>>" + res);
        log.info("上传Strava完成>>>>>>>" + filePath);

    }


    /**
     * 获取行者用户id
     *
     * @return
     */
    private static String getXZUserId() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", XZ_COOKIE);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> exchange = restTemplate.exchange(XZ_USER_INFO, HttpMethod.GET, entity, String.class);

        String res = exchange.getBody();
        String userId = JSONObject.parseObject(res).getString("userid");

        log.info("获取到行者用户id>>>>>>" + userId);
        return userId;
    }


    /**
     * 获取Strava csrf token
     *
     * @return
     */
    private static String getStravaCsrfToken() {

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", STRAVA_COOKIE);


        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<String> exchange = restTemplate.exchange(STRAVA_CSRF_TOKEN_URL, HttpMethod.GET, entity, String.class);

        String res = exchange.getBody();

        Document document = Jsoup.parse(res);
        Element element = document.selectFirst("meta[name='csrf-token']");

        String csrfToken = element.attr("content");

        log.info("获取到csrfToken>>>>>>>>>" + csrfToken);
        return csrfToken;
    }


}
