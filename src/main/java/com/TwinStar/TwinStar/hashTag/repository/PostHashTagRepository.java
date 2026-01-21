package com.TwinStar.TwinStar.hashTag.repository;


import com.TwinStar.TwinStar.hashTag.domain.PostHashTag;
import com.TwinStar.TwinStar.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostHashTagRepository extends JpaRepository<PostHashTag,Long> {
//    PostHashTag와 post를 조인하고 해시태그이름이 있는 것을 posthashtag타입으로 반환 
    @Query("SELECT pht FROM PostHashTag pht JOIN FETCH pht.post WHERE pht.hashTag.hashTagName = :hashTag")
    List<PostHashTag> findByHashTag(@Param("hashTag") String hashTag);

//    특정 게시물에 연결된 해시태그 리스트 반환
    List<PostHashTag> findByPost(Post post);

    //    특정 포스트의 모든 해시태그 삭제
    void deleteByPost(Post post);

    @Query("SELECT pht FROM PostHashTag pht JOIN FETCH pht.hashTag WHERE pht.post IN :posts")
    List<PostHashTag> findByPostIn(@Param("posts") List<Post> posts);
}
