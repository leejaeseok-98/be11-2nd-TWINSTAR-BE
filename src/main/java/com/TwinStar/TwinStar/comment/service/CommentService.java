package com.TwinStar.TwinStar.comment.service;

import com.TwinStar.TwinStar.alarm.service.AlarmService;
import com.TwinStar.TwinStar.comment.domain.Comment;
import com.TwinStar.TwinStar.comment.dto.CommentCreateReqDto;
import com.TwinStar.TwinStar.comment.dto.CommentUpdateReqDto;
import com.TwinStar.TwinStar.comment.dto.ReplyCommentCreateReqDto;
import com.TwinStar.TwinStar.comment.repository.CommentRepository;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.repository.PostRepository;
import com.TwinStar.TwinStar.user.domain.User;
import com.TwinStar.TwinStar.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final AlarmService alarmService;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository, PostRepository postRepository, AlarmService alarmService) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.alarmService = alarmService;
    }

    public Long create(CommentCreateReqDto dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()-> new EntityNotFoundException("user is not found."));
        Post post = postRepository.findById(dto.getPostId()).orElseThrow(()-> new EntityNotFoundException("post is not found."));
        Comment comment = Comment.builder()
                .user(user)
                .post(post)
                .content(dto.getContent())
                .build();
        commentRepository.save(comment);

//        게시물 작성자에게 알림 보내기
        User receiver = post.getUser();
        String content = user.getNickName()+"님이 회원님의 게시물에 댓글을 작성했습니다.";
        String url = "https://www.alexandrelax.store/post/detail/"+post.getId();
        alarmService.createAlarm(receiver, content, url);

        return dto.getPostId();
    }

    public Long update(CommentUpdateReqDto dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user not found"));
        Comment comment = commentRepository.findById(dto.getCommentId()).orElseThrow(()-> new EntityNotFoundException("comment is not found."));
        User commentWriteUser = comment.getUser();

        if (!loginUser.equals(commentWriteUser)){ return 0L; }
        comment.updateContent(dto.getContent());
        commentRepository.save(comment);

        return comment.getPost().getId();
    }

    public Long delete(Long commentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User loginUser = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user not found"));
        Comment comment = commentRepository.findById(commentId).orElseThrow(()-> new EntityNotFoundException("comment is not found."));
        User commentWriteUser = comment.getUser();

        if (!loginUser.equals(commentWriteUser)){ return 0L; }
        comment.delete();
        commentRepository.save(comment);

        return comment.getPost().getId();
    }

    public Long replyCreate(ReplyCommentCreateReqDto dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user not found"));
        Comment parent = commentRepository.findById(dto.getParentId()).orElseThrow(()-> new EntityNotFoundException("comment is not found."));
        Post post = commentRepository.findPostByParentId(parent.getId());

        Comment comment = Comment.builder()
                .user(user)
                .post(post)
                .parent(parent)
                .content(dto.getContent())
                .build();
        commentRepository.save(comment);

        parent.addChild(comment);
        return post.getId();
    }


    public void pinned(Long commentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userRepository.findById(Long.valueOf(authentication.getName())).orElseThrow(()->new EntityNotFoundException("user is not found"));
        Comment comment = commentRepository.findById(commentId).orElseThrow(()->new EntityNotFoundException("comment is not found"));
        User postWriter = comment.getPost().getUser();
        if(!postWriter.equals(user)){ return; }

//        대댓글이 아니라면
        if(comment.getParent() == null){
            comment.pinned();
        }

    }
}
