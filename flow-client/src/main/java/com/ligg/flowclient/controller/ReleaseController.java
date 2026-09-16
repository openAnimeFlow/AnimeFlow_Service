package com.ligg.flowclient.controller;

import com.ligg.flowclient.module.vo.ProjectReleaseVo;
import com.ligg.flowclient.service.ReleaseService;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/releases")
public class ReleaseController {

    private final ReleaseService releaseService;

    @GetMapping
    public ResponseEntity<Result<List<ProjectReleaseVo>>> getReleases(
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "参数异常")
            @Max(value = 100, message = "参数异常")
            long page) {
        try {
            List<ProjectReleaseVo> releases = releaseService.getReleases(page);
            return ResponseEntity.ok()
                    .header("Cache-Control", "public, max-age=3600")
                    .body(Result.success(ResponseCode.SUCCESS, releases));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.error(ResponseCode.ERROR, "Unable to fetch project releases"));
        }
    }

}
