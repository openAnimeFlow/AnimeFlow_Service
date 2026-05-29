/**
 * @author Ligg
 * @date 2026/5/28 02:45
 */
package com.ligg.common.utils;

import com.ligg.common.constants.ApiConstant;
import com.ligg.common.model.CoverImages;
import com.ligg.common.model.ImageFour;
import com.ligg.common.model.ImageUrls;

public final class Utils {

    /**
     * 原地替换图片 URL 为 wsrv CDN。
     * 所有 {@link ImageUrls}（small / medium / large）均适用；
     * {@link CoverImages} 额外处理 common / grid；{@link ImageFour} 额外处理 grid。
     */
    public static <T extends ImageUrls> void applyWsrvCdnInPlace(T images) {
        if (images == null) {
            return;
        }
        images.setSmall(imgUrlToWsrvCdn(images.getSmall()));
        images.setMedium(imgUrlToWsrvCdn(images.getMedium()));
        images.setLarge(imgUrlToWsrvCdn(images.getLarge()));
        if (images instanceof CoverImages coverImages) {
            coverImages.setCommon(imgUrlToWsrvCdn(coverImages.getCommon()));
            coverImages.setGrid(imgUrlToWsrvCdn(coverImages.getGrid()));
        } else if (images instanceof ImageFour imageFour) {
            imageFour.setGrid(imgUrlToWsrvCdn(imageFour.getGrid()));
        }
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
