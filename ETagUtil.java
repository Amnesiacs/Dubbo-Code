package com.ziyuan.etag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class ETagUtil {

    public static boolean judgeEtag(HttpServletRequest request, HttpServletResponse response, String tag) {
        response.setHeader("ETag", tag);
        String previousTag = request.getHeader("If-None-Match");
        boolean flag = false;
        if (previousTag != null && previousTag.equals(tag)) {
            try {
                response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
                // use the same date we sent when we created the ETag the first time through
                response.setHeader("Last-Modified", request.getHeader("If-Modified-Since"));
                flag = true;
            } catch (IOException e) {
                flag = false;
            }
        } else {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MILLISECOND, 0);
            Date lastModified = cal.getTime();
            response.setDateHeader("Last-Modified", lastModified.getTime());
        }
        return flag;
    }
}