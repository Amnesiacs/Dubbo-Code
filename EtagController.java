package com.ziyuan.etag;

import com.alibaba.fastjson.JSON;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * EtagController
 *
 * @author ziyuan
 * @since 2017-09-13
 */
@RestController
public class EtagController {

    private static List<Demo> demos = new ArrayList<>();

    static {
        demos.add(new Demo("ziyuan", "123"));
    }

    @ResponseBody
    @RequestMapping(value = "/get", method = {RequestMethod.POST, RequestMethod.GET})
    public ResultUtil etag(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String tag = String.valueOf(demos.size());
        response.setHeader("ETag", tag);
        String previousTag = request.getHeader("If-None-Match");
        if (previousTag != null && previousTag.equals(tag)) {
            response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            response.setHeader("Last-Modified", request.getHeader("If-Modified-Since"));
            return new ResultUtil();
        } else {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MILLISECOND, 0);
            Date lastModified = cal.getTime();
            response.setDateHeader("Last-Modified", lastModified.getTime());
        }
        return new ResultUtil().setSuccessData(demos);
    }

    @ResponseBody
    @RequestMapping(value = "/add", method = {RequestMethod.POST, RequestMethod.GET})
    public ResultUtil etagAdd() {
        demos.add(new Demo("juecai", "123"));
        return new ResultUtil().setSuccessData(demos);
    }
}
