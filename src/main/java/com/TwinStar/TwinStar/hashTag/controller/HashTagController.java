package com.TwinStar.TwinStar.hashTag.controller;


import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.hashTag.domain.HashTag;
import com.TwinStar.TwinStar.hashTag.service.HashTagService;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.dto.PostListResDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hashtags")
public class HashTagController {
    private final HashTagService hashTagService;


    public HashTagController(HashTagService hashTagService) {
        this.hashTagService = hashTagService;
    }

//    특정 게시물의 해시태그 조회
    public ResponseEntity<?> getHashTagsByPost(@PathVariable Long postId){
        Post post = hashTagService.findPostById(postId);
        if (post == null) {
            return new ResponseEntity<>(new CommonDto(HttpStatus.NOT_FOUND.value(), "게시물을 찾을 수 없습니다.", null), HttpStatus.NOT_FOUND);
        }
        List<String> hashTags = hashTagService.getHashTagsByPost(post);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물의 해시태그 목록입니다.",hashTags),HttpStatus.OK);
    }

}
