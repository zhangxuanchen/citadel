package cn.com.app.security.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaptchaResponse(
        String captchaId,
        String image,
        String captchaCode
) {
}
