/**
 * @author Ligg
 * @date 2026/5/28 02:45
 */

package com.ligg.common.utils;


import com.ligg.common.constants.ApiConstant;
import com.ligg.common.model.CoverImages;


public final class Utils {

    /**
     * 原地替换 {@link CoverImages} 五档封面 URL
     */
    public static void applyWsrvCdnInPlace(CoverImages images) {

        if (images == null) {
            return;

        }
        images.setLarge(imgUrlToWsrvCdn(images.getLarge()));
        images.setCommon(imgUrlToWsrvCdn(images.getCommon()));
        images.setMedium(imgUrlToWsrvCdn(images.getMedium()));
        images.setSmall(imgUrlToWsrvCdn(images.getSmall()));
        images.setGrid(imgUrlToWsrvCdn(images.getGrid()));
    }

    /**
     * 单张图片 URL 转 wsrv CDN。
     */
    public static String imgUrlToWsrvCdn(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return imageUrl;
        }
        if (imageUrl.startsWith(ApiConstant.WSRV_CDN)) {
            return imageUrl;
        }
        return ApiConstant.WSRV_CDN + "/?url=" + imageUrl;
    }

}