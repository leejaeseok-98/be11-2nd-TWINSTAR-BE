package com.TwinStar.TwinStar.comment.controller;

import com.TwinStar.TwinStar.comment.dto.CommentCreateReqDto;
import com.TwinStar.TwinStar.comment.dto.CommentLikeResDto;
import com.TwinStar.TwinStar.comment.dto.CommentUpdateReqDto;
import com.TwinStar.TwinStar.comment.dto.ReplyCommentCreateReqDto;
import com.TwinStar.TwinStar.comment.service.CommentLikeService;
import com.TwinStar.TwinStar.comment.service.CommentService;
import com.TwinStar.TwinStar.common.dto.CommonDto;
import com.TwinStar.TwinStar.user.dto.UserListResDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RequestMapping("/comment")
@RestController
public class CommentController {
    private final CommentService commentService;
    private final CommentLikeService commentLikeService;

    public CommentController(CommentService commentService, CommentLikeService commentLikeService) {
        this.commentService = commentService;
        this.commentLikeService = commentLikeService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createComment(@RequestBody CommentCreateReqDto dto){
        log.info("[CommentController] 댓글 작성 요청 - postId: {}", dto.getPostId());
        Long postId = commentService.create(dto);
        log.info("[CommentController] 댓글 작성 완료 - postId: {}", postId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "댓글 작성 완료",postId),HttpStatus.OK);
    }

    @PostMapping("/delete/{commentId}")
    public ResponseEntity<?> createComment(@PathVariable Long commentId){
        Long postId = commentService.delete(commentId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "댓글 삭제 완료",postId),HttpStatus.OK);
    }

    @PatchMapping("/update")
    public ResponseEntity<?> updateComment(@RequestBody CommentUpdateReqDto dto){
        Long postId = commentService.update(dto);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "댓글 수정 완료",postId),HttpStatus.OK);
    }

    @PostMapping("/reply")
    public ResponseEntity<?> createComment(@RequestBody ReplyCommentCreateReqDto dto){
        Long postId = commentService.replyCreate(dto);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "댓글 작성 완료",postId),HttpStatus.OK);
    }

    @PostMapping("/like/{commentId}")
    public ResponseEntity<?> commentLike(@PathVariable Long commentId) {
        CommentLikeResDto commentLikeInfo = commentLikeService.commentLikeToggle(commentId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "댓글 좋아요 기능 성공", commentLikeInfo), HttpStatus.OK);
    }

    @PostMapping("/pinned/{commentId}")
    public ResponseEntity<?> commentPinnede(@PathVariable Long commentId){
        commentService.pinned(commentId);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(),"댓글 고정 완료",commentId),HttpStatus.OK);
    }

    @GetMapping("/like/list/{commentId}")
    public ResponseEntity<?> getLikeList(@PathVariable Long commentId, @PageableDefault(size = 10) Pageable pageable) {
        Page<UserListResDto> postDetailResDto = commentLikeService.getLikeList(commentId, pageable);
        return new ResponseEntity<>(new CommonDto(HttpStatus.OK.value(), "댓글 좋아요 리스트 조회 완료", postDetailResDto), HttpStatus.OK);
    }
}
