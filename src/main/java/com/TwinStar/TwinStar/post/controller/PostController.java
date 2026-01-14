package com.TwinStar.TwinStar.post.controller;

import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.post.dto.*;
import com.TwinStar.TwinStar.post.service.PostLikeService;
import com.TwinStar.TwinStar.post.service.PostService;
import com.TwinStar.TwinStar.user.dto.UserListResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/post")
@RestController
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;

    public PostController(PostService postService, PostLikeService postLikeService) {
        this.postService = postService;
        this.postLikeService = postLikeService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@ModelAttribute PostCreateReqDto dto){
        log.info("[PostController] 게시물 작성 요청");
        Long postId = postService.save(dto);
        log.info("[PostController] 게시물 작성 완료 - postId: {}", postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물 작성 완료",postId),HttpStatus.OK);
    }

    @PostMapping("delete/{postId}")
    public ResponseEntity<?> delete(@PathVariable Long postId){
        log.info("[PostController] 게시물 삭제 요청 - postId: {}", postId);
        postService.delete(postId);
        log.info("[PostController] 게시물 삭제 완료 - postId: {}", postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물 삭제 완료", postId), HttpStatus.OK);
    }

    @GetMapping("/update/{postId}")
    public ResponseEntity<?> getUpdate(@PathVariable Long postId){
        PostUpdateResDto dto = postService.getUpdateDataRes(postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물 데이터 반환",dto),HttpStatus.OK);
    }

    @PostMapping("/update/{postId}")
    public ResponseEntity<?> patchUpdate(@PathVariable Long postId, @RequestBody PostUpdateReqDto dto) {
        log.info("[PostController] 게시물 수정 요청 - postId: {}", postId);
        postService.Update(postId, dto); // postId가 있으면 기존 게시물 수정
        log.info("[PostController] 게시물 수정 완료 - postId: {}", postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물 수정 완료", postId), HttpStatus.OK);
    }

    @PostMapping("/like/{postId}")
    public ResponseEntity<?> postLike(@PathVariable Long postId){
        PostLikeResDto likeInfo = postLikeService.togglePostLike(postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"게시물 좋아요 완료",likeInfo),HttpStatus.OK);
    }

    @GetMapping("/list")
    public ResponseEntity<?> getPostList(@RequestParam(name = "page", defaultValue = "0") Integer page,
                                                @RequestParam(name = "size", defaultValue = "5") Integer size) {
        Page<PostListResDto> postListResDtoPage = postService.getList(page,size);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"게시물 리스트 불러오기 완료",postListResDtoPage),HttpStatus.OK);
    }

    @GetMapping("/list/{hashtag}")
    public ResponseEntity<?> getHashtagPostList(@PathVariable String hashtag, @RequestParam(name = "page", defaultValue = "0") Integer page,
                                                @RequestParam(name = "size", defaultValue = "5") Integer size){
        Page<PostListResDto> getHashtagPostList = postService.getHashtagPostList(hashtag,page,size);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "해시태그 게시물 조회 완료", getHashtagPostList),HttpStatus.OK);
    }


    @GetMapping("/detail/{postId}")
    public ResponseEntity<?> getPostDetail(@PathVariable Long postId) {
        log.debug("[PostController] 게시물 상세 조회 요청 - postId: {}", postId);
        PostDetailResDto postDetailResDto = postService.getDetail(postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물 조회 완료", postDetailResDto), HttpStatus.OK);
    }

    @GetMapping("/like/list/{postId}")
    public ResponseEntity<?> getLikeList(@PathVariable Long postId, @PageableDefault(size = 10) Pageable pageable) {
        Page<UserListResDto> postDetailResDto = postService.getLikeList(postId, pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "게시물 조회 완료", postDetailResDto), HttpStatus.OK);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }

}
