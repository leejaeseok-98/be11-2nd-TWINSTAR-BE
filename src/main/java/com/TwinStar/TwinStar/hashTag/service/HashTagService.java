package com.TwinStar.TwinStar.hashTag.service;


import com.TwinStar.TwinStar.hashTag.domain.HashTag;
import com.TwinStar.TwinStar.hashTag.domain.PostHashTag;
import com.TwinStar.TwinStar.hashTag.repository.HashTagRepository;
import com.TwinStar.TwinStar.hashTag.repository.PostHashTagRepository;
import com.TwinStar.TwinStar.post.domain.Post;
import com.TwinStar.TwinStar.post.repository.PostRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class HashTagService {
    private final HashTagRepository hashTagRepository;
    private final PostHashTagRepository postHashTagRepository;
    private final PostRepository postRepository;

    public HashTagService(HashTagRepository hashTagRepository, PostHashTagRepository postHashTagRepository, PostRepository postRepository) {
        this.hashTagRepository = hashTagRepository;
        this.postHashTagRepository = postHashTagRepository;
        this.postRepository = postRepository;
    }

//    해시태그 찾기 or 해시태그가 없으면 저장
    public HashTag findOrCreateHashTag(String hashTagName){
        return hashTagRepository.findByHashTagName(hashTagName).orElseGet(()->hashTagRepository
                .save(HashTag.builder().hashTagName(hashTagName).build())); //orElseGet은 앞에 메서드가 null값일 때. 매개변수에 있는 값을 호출함. 기본값이 있으면 실행하지 않음
    }

//    해시태그로 게시물 조회
    @Transactional(readOnly = true)
    public List<Post> findPostsByHashTag(HashTag hashTagName,int page, int size){
        if (hashTagName == null || hashTagName.getHashTagName() == null) {
            throw new IllegalArgumentException("해시태그 값이 null입니다.");
        }
//      해시태그를 조회해서 post게시물을 가져오기 위한 변수
        List<PostHashTag> postHashTags = postHashTagRepository.findByHashTag(hashTagName.getHashTagName());

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime")); // 최신순 정렬

        if (postHashTags.isEmpty()) {
            System.out.println("해당 해시태그로 등록된 게시물이 없습니다.");
        }

        return postHashTags.stream().map(PostHashTag::getPost).collect(Collectors.toList());
    }

//  hashtag객체 찾기
    public HashTag findByName(String hashtag){
        return hashTagRepository.findByHashTagName(hashtag).orElseThrow(()->new EntityNotFoundException("해시태그 없다"));
    }

    public List<String> getHashTagsByPost(Post post){
        if (post == null) {
            throw new IllegalArgumentException("게시물 값이 null입니다.");
        }
        List<PostHashTag> postHashTags = postHashTagRepository.findByPost(post);

        return postHashTags.stream().map
                (postHashTag -> postHashTag.getHashTag().getHashTagName()).collect(Collectors.toList());

    }
//  특정 postId로 게시물 찾기
    public Post findPostById(Long postId){
        return postRepository.findById(postId).orElseThrow(() -> new EntityNotFoundException("해당 게시물을 찾을 수 없습니다."));
    }

//  특정 게시물의 모든 해시태그 삭제
    public void removeAllHashtagsFromPost(Post post){
        postHashTagRepository.deleteByPost(post);
    }
}
